# Patient Management System

A containerized healthcare platform built with Spring Boot microservices.

## Services

- `api-gateway` - routes requests and validates JWTs
- `auth-service` - authentication and JWT issuance
- `patient-service` - patient management and Kafka event publishing
- `billing-service` - billing communication over gRPC
- `analytics-service` - consumes patient events from Kafka

## Technology

Java 21, Spring Boot, Spring Cloud Gateway, PostgreSQL, Apache Kafka,
gRPC, Docker Compose, Maven, AWS CDK, Amazon ECS/Fargate, Amazon ECR,
Amazon RDS, Amazon MSK, Application Load Balancer, IAM, and CloudWatch.

## Run locally

1. Copy `.env.example` to `.env` and replace the development values.
2. Start the complete local stack:

   ```bash
   docker compose up --build
   ```

3. The main endpoints are:

   - Gateway: `http://localhost:4004`
   - Auth service: `http://localhost:4005`
   - Patient service: `http://localhost:4000`
   - Billing REST/health endpoint: `http://localhost:4001/actuator/health`
   - Analytics health endpoint: `http://localhost:4002/actuator/health`
   - Adminer: `http://localhost:8080`

Kafka is available to the application containers at `kafka:9092`.

## LocalStack infrastructure

The free LocalStack deployment is intentionally limited to resources supported
by the local environment. It excludes RDS, MSK, Cloud Map, and the public load
balancer; PostgreSQL and application containers remain managed by Compose.

From Git Bash:

```bash
cd infrastructure
./localstack-deploy.sh
```

To create the LocalStack ALB hostname shown in the architecture demo, use a
LocalStack plan that includes ELBv2 and start the LocalStack container with
`LOCALSTACK_AUTH_TOKEN`. Then run:

```bash
LOCALSTACK_ALB=true ./localstack-deploy.sh
```

The script will print a hostname such as
`lb-12345678.elb.localhost.localstack.cloud`. The free LocalStack edition
cannot create this resource and will return a license error.

## AWS infrastructure

The non-LocalStack CDK synthesis creates the full AWS architecture, including
VPC networking, ECS/Fargate services, ECR repositories, RDS, MSK, IAM, logging,
and the Application Load Balancer.

Build and push service images to the ECR repositories created by CDK before
starting ECS services. Use an AWS role or GitHub Actions OIDC; do not commit
AWS access keys or production secrets.

For a first deployment, create the ECR repositories with the infrastructure
stack, push the five images, and then update the same stack so ECS can pull
the images. Subsequent deployments can build, push, and update the stack in
one pipeline.

The AWS path must be synthesized without `LOCALSTACK_MODE=true` and deployed
with the normal AWS CLI/CDK credentials, not with
`--endpoint-url=http://localhost:4566`.

## Validation

Every service is built and tested in GitHub Actions. The integration tests in
`integration-tests` expect the local Compose stack to be running and can be
executed separately after startup.
