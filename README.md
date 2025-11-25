
# my-java-microservice

Composed of multiple small Spring Boot microservices for demo/learning purposes. This repository contains a collection of services and supporting modules used for patient management, authentication, billing, analytics, and an API gateway.


<img width="1087" height="446" alt="Screenshot 2025-11-25 225921" src="https://github.com/user-attachments/assets/32426d9b-155a-4ce9-836a-b92c1689e424" />

## Repository layout

- `analytics-service/` — analytics microservice (Maven project, Dockerfile).
- `api-gateway/` — gateway routing and entrypoint for the system.
- `auth-service/` — authentication and authorization service.
- `patient-service/` — patient management service (create/read/update/delete).
- `billiing-service/` — billing-related service (note: folder name uses the project's existing spelling).
- `infrastructure/` — infrastructure utilities (includes `localstack-deploy.sh` used for local AWS emulation).
- `integration-tests/` — integration tests project.
- `grpc-requests/`, `api-requests/`, `auth-requests/` — example HTTP/gRPC request snippets useful during development.

There are also IDE files (IntelliJ `.iml` / `.idea`) included for convenience.

## Quick prerequisites

- Java 11+ (or the version configured in each module's `pom.xml`).
- Maven (or use the included Maven wrapper `mvnw` / `mvnw.cmd`).
- Docker (optional, for building/running images defined by each module's `Dockerfile`).

Note: Use the included Maven wrapper to ensure consistent Maven versions across machines.

## Build and run (local development)

Each service is a standard Maven module. From the root of the `my-java-microservice` folder you can run builds for a single module or all modules.

Build everything (from Windows PowerShell):

```powershell
.\mvnw.cmd -T 1C clean install -DskipTests
```

Build and run a single service (example: `patient-service`):

```powershell
cd patient-service
..\\mvnw.cmd spring-boot:run
```

Or using the wrapper from the root (executes module lifecycle):

```powershell
.\mvnw.cmd -pl patient-service -am -DskipTests spring-boot:run
```

Notes:
- Replace `patient-service` with any other module folder name (e.g., `auth-service`, `analytics-service`).
- If you prefer Maven directly, use `mvn` instead of `mvnw.cmd`.

## Docker

Each service contains a `Dockerfile`. Example: build and run `patient-service` image:

```powershell
cd patient-service
docker build -t my/patient-service:local .
docker run --rm -p 8081:8080 --name patient-service my/patient-service:local
```

Adjust ports and environment variables as required by the service's `application.yml` or arguments.

## gRPC / Protobuf

Some services include `proto/` files (e.g. `patient-service`, `billing-service`). These are compiled during the Maven build. If you modify `.proto` files, re-run the Maven build so generated sources are refreshed.

## Local AWS emulation (LocalStack)

The `infrastructure/localstack-deploy.sh` script is provided to help deploy or test against LocalStack. That script expects a Unix-like shell; on Windows you can run it via WSL or adapt the steps for PowerShell. LocalStack is useful when services interact with AWS resources locally.

## Integration tests

Integration tests live under `integration-tests/`. These are Maven-based and can be executed with:

```powershell
.\mvnw.cmd -pl integration-tests test
```

Run an individual module's tests using its own `mvnw.cmd` inside the module directory.

## Common developer tasks & commands

- Run all unit tests:

```powershell
.\mvnw.cmd -T 1C test
```

- Run static analysis (if configured in modules):

```powershell
.\mvnw.cmd verify
```

- Clean build with Docker images (example):

```powershell
.\mvnw.cmd clean package -DskipTests
cd patient-service
docker build -t my/patient-service:latest .
```

