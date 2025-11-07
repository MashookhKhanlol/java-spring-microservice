#!/bin/bash
set -e

ENDPOINT="http://localhost:4566"
STACK_NAME="patient-management"
TEMPLATE_FILE="./cdk.out/localstack.template.json"

echo "🧹 Deleting old stack..."
aws --endpoint-url=$ENDPOINT cloudformation delete-stack --stack-name $STACK_NAME || true

echo "⏳ Waiting for deletion..."
aws --endpoint-url=$ENDPOINT cloudformation wait stack-delete-complete --stack-name $STACK_NAME || true

echo "🪄 Ensuring CDK bootstrap parameter exists..."
aws --endpoint-url=$ENDPOINT ssm put-parameter \
  --name /cdk-bootstrap/hnb659fds/version \
  --type String \
  --value "10" \
  --overwrite >/dev/null 2>&1 || true

echo "🚀 Deploying new stack..."
aws --endpoint-url=$ENDPOINT cloudformation deploy \
  --stack-name $STACK_NAME \
  --template-file "$TEMPLATE_FILE" \
  --parameter-overrides BootstrapVersion=10
