package com.pm.stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;

public class LocalStack extends Stack {
    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDb =
                createDatabase("AuthServiceDB", "authservicedb");

        DatabaseInstance patientServiceDb =
                createDatabase("PatientServiceDB", "patientservicedb");

        // Note: MSK cluster creation commented out due to LocalStack limitations
        // Kafka will be accessed via external broker or you can uncomment if using real AWS
        // CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        FargateService authService =
                createFargateService("AuthService",
                        "auth-service",
                        List.of(4005),
                        authServiceDb,
                        Map.of("JWT_SECRET", "Y2hhVEc3aHJnb0hYTzMyZ2ZqVkpiZ1RkZG93YWxrUkM="));

        authService.getNode().addDependency(authServiceDb);

        FargateService billingService =
                createFargateService("BillingService",
                        "billing-service",
                        List.of(4001, 9001),
                        null,
                        null);

        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null);

        // analyticsService.getNode().addDependency(mskCluster); // Removed MSK dependency

        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "billing-service.patient-management.local",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ));
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(billingService);
        // patientService.getNode().addDependency(mskCluster); // Removed MSK dependency

        createApiGatewayService();
    }

    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .natGateways(1)
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_16_6)
                                .build()))
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .publiclyAccessible(false)
                .build();
    }

    private CfnCluster createMskCluster(){
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("3.5.1")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.t3.small")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .limit(2)
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .storageInfo(CfnCluster.StorageInfoProperty.builder()
                                .ebsStorageInfo(CfnCluster.EBSStorageInfoProperty.builder()
                                        .volumeSize(10)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private Cluster createEcsCluster(){
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .clusterName("patient-management-cluster")
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {

        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                                .build()));

        Map<String, String> envVars = new HashMap<>();
        // Using external Kafka broker - adjust as needed for your LocalStack Kafka setup
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092");

        if(additionalEnvVars != null){
            envVars.putAll(additionalEnvVars);
        }

        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", String.format(
                    "jdbc:postgresql://%s:%s/%s",
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    db.getInstanceIdentifier()
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD",
                    db.getSecret().secretValueFromJson("password").unsafeUnwrap());
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .cloudMapOptions(software.amazon.awscdk.services.ecs.CloudMapOptions.builder()
                        .name(imageName)
                        .build())
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .build();
    }

    private void createApiGatewayService() {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL", "http://auth-service.patient-management.local:4005"
                        ))
                        .portMappings(List.of(PortMapping.builder()
                                .containerPort(4004)
                                .protocol(Protocol.TCP)
                                .build()))
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .publicLoadBalancer(true)
                        .listenerPort(80)
                        .healthCheckGracePeriod(Duration.seconds(60))
                        .taskSubnets(SubnetSelection.builder()
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .build())
                        .build();

        apiGateway.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/actuator/health")
                .interval(Duration.seconds(30))
                .timeout(Duration.seconds(5))
                .healthyThresholdCount(2)
                .unhealthyThresholdCount(3)
                .build());
    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}