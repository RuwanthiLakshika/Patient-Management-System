#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

if aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
    --stack-name patient-management >/dev/null 2>&1; then
    aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
        --stack-name patient-management
    aws --endpoint-url=http://localhost:4566 cloudformation wait stack-delete-complete \
        --stack-name patient-management
fi

LOCALSTACK_MODE=true mvn -q compile exec:java \
    -Dexec.mainClass=com.pm.stack.LocalStack

aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file "cdk.out/localstack.template.json"

aws --endpoint-url=http://localhost:4566 cloudformation describe-stacks \
    --stack-name patient-management \
    --query "Stacks[0].StackStatus" --output text

if [ "${LOCALSTACK_ALB:-false}" = "true" ]; then
    aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
        --query "LoadBalancers[0].DNSName" --output text
fi