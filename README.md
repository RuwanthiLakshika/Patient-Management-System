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

### Frontend

The React frontend lives in `frontend/` and connects to the API gateway through
Vite's local proxy. Start the backend stack first, then run:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The sign-in form uses the seeded account in
`auth-service/src/main/resources/data.sql`; patient records can be searched,
created, edited, and deleted from the dashboard.

Frontend code is organized by responsibility:

```text
frontend/src/
├── api/client.js                 # Authenticated API clients
├── components/Icon.jsx           # Shared UI primitive
├── features/auth/Login.jsx       # Authentication flow
├── features/dashboard/Dashboard.jsx
├── features/patients/PatientModal.jsx
├── constants.js
└── App.jsx                       # Application composition
```

The dashboard also exposes the service interactions: creating a patient calls
the billing service over gRPC and publishes a patient event to Kafka. The
frontend reads the billing projection from `/api/billing/accounts` and the
analytics projection from `/api/analytics/overview` through authenticated
gateway routes. These demo projections are held in service memory and reset
when the billing or analytics containers restart.

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

<img width="3200" height="1800" alt="develop" src="https://github.com/user-attachments/assets/e1e90d04-2154-4f22-8009-e642363f308d" />

<img width="3200" height="2080" alt="deploy" src="https://github.com/user-attachments/assets/0280e7a6-456c-4b4e-926e-3bda949e9fd6" />

<img width="1802" height="827" alt="image (5)" src="https://github.com/user-attachments/assets/ede78944-b0db-42ee-a7c3-7a9a4e31b2bc" />

