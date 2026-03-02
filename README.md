# AI Marketing Platform

Full-stack marketing automation platform with AI-powered campaign management, creative analysis, and performance insights. Built for digital agencies managing multiple client accounts on Meta (Facebook/Instagram).

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Backend](#backend)
- [Frontend](#frontend)
- [Database](#database)
- [Testing](#testing)
- [Docker](#docker)
- [Infrastructure](#infrastructure)
- [CI/CD](#cicd)
- [API Documentation](#api-documentation)

---

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Frontend   │────▶│   Backend    │────▶│  PostgreSQL   │
│  Vue 3 SPA   │     │ Spring Boot  │     │    16 (RDS)   │
└──────────────┘     └──────┬───────┘     └──────────────┘
       │                    │
       │              ┌─────┴─────┐
  CloudFront          │   Redis   │
  (CDN + S3)          │  (Cache)  │
                      └───────────┘
```

**Key patterns:**
- Multi-tenant architecture (agency-scoped data isolation via `TenantContext`)
- Modular monolith (8 domain packages, package-private by default)
- Flyway-managed schema migrations
- Structured JSON logging with correlation IDs
- Redis caching with `@Cacheable` / `@CacheEvict`

---

## Tech Stack

| Layer          | Technology                                 |
|----------------|--------------------------------------------|
| Backend        | Java 21, Spring Boot 3.5, Maven             |
| Frontend       | Vue 3, Vuetify 4, Pinia, Vue Router, Axios |
| Database       | PostgreSQL 16, Flyway                       |
| Cache          | Redis 7                                     |
| Logging        | Logback + Logstash JSON Encoder             |
| API Docs       | SpringDoc OpenAPI (Swagger UI)              |
| Testing        | JUnit 5, Mockito, Testcontainers            |
| Infrastructure | Terraform, AWS (ECS Fargate, Aurora, S3)    |
| CI/CD          | GitHub Actions                              |
| Containers     | Docker multi-stage builds                   |

---

## Project Structure

```
ai-marketing-platform/
├── backend/                    # Spring Boot API
│   ├── src/main/java/com/amp/
│   │   ├── agency/             # Agency management
│   │   ├── ai/                 # AI suggestions & action logs
│   │   ├── audit/              # Audit trail
│   │   ├── auth/               # Security & dev auth filter
│   │   ├── campaigns/          # Campaigns, adsets, ads
│   │   ├── clients/            # Client profiles & management
│   │   ├── common/             # OpenAPI config, exceptions
│   │   ├── creatives/          # Assets, packages, copy variants
│   │   ├── insights/           # Performance KPIs & daily data
│   │   ├── meta/               # Meta (Facebook) integration
│   │   ├── ops/                # Logging, caching, health
│   │   ├── reports/            # Reports & feedback
│   │   └── tenancy/            # Multi-tenant context
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway V001–V006
│   │   ├── application.properties
│   │   ├── application-local.yml
│   │   └── logback-spring.xml
│   ├── src/test/               # Unit + integration tests
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                   # Vue 3 SPA
│   ├── src/
│   │   ├── views/              # 8 views (Dashboard → Meta)
│   │   ├── stores/             # 7 Pinia stores
│   │   ├── router/             # Vue Router config
│   │   ├── api/                # Axios HTTP client
│   │   └── plugins/            # Vuetify setup
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── infra/terraform/            # AWS infrastructure (7 modules)
│   ├── modules/{vpc,aurora,s3,sqs,ecs,cdn,monitoring}/
│   ├── environments/{staging,prod}.tfvars
│   └── main.tf
├── scripts/                    # Utility scripts
│   ├── db-seed.sql             # Development seed data
│   └── db-seed.sh
├── docs/                       # Specification documents
├── .github/workflows/          # CI/CD pipelines
│   ├── backend-ci.yml
│   ├── frontend-ci.yml
│   └── deploy.yml
└── docker-compose.yml          # Local development services
```

---

## Getting Started

### Prerequisites

- Java 21 (Temurin recommended)
- Node.js 20+
- Docker & Docker Compose
- Maven (or use the included `mvnw` wrapper)

### 1. Start infrastructure services

```bash
docker compose up -d
```

This starts PostgreSQL 16, Redis 7, and Mailpit.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API starts on **http://localhost:8080** with the `local` profile.

### 3. Seed the database (optional)

```bash
./scripts/db-seed.sh
```

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on **http://localhost:5173** with hot-reload.

### 5. Explore the API

Open **http://localhost:8080/swagger-ui.html** for interactive API docs.

---

## Backend

### Domain Modules (118 Java source files)

| Module       | Description                                          | Key Entities                           |
|-------------|------------------------------------------------------|---------------------------------------|
| `agency`     | Agency CRUD                                          | Agency                                 |
| `clients`    | Client management with business profiles             | Client, ClientProfile                  |
| `campaigns`  | Campaign lifecycle (create → publish)                | Campaign, Adset, Ad                    |
| `creatives`  | Creative assets, packages, copy variants, analysis   | CreativeAsset, CreativePackage, CopyVariant |
| `insights`   | Performance metrics and KPI dashboards               | InsightDaily, KpiSummary               |
| `ai`         | AI-powered suggestions with action logging           | AiSuggestion, AiActionLog             |
| `reports`    | Report generation, approval, and feedback            | Report, Feedback                       |
| `meta`       | Meta (Facebook) OAuth connections and sync jobs       | MetaConnection, MetaSyncJob            |
| `audit`      | Immutable audit trail for all state changes           | AuditLog                               |
| `ops`        | Correlation IDs, request logging, Redis cache/health  | –                                      |
| `auth`       | Spring Security config, dev auth filter (`@Profile("local")`) | –                             |
| `tenancy`    | ThreadLocal-based multi-tenant context               | TenantContext                          |

### Configuration Profiles

| Profile   | Purpose                    | Datasource        | Cache    | Logging |
|-----------|----------------------------|--------------------|----------|---------|
| `local`   | Development                | localhost:5432     | Redis    | Console |
| `staging` | Staging environment        | Aurora RDS         | Redis    | JSON    |
| `prod`    | Production                 | Aurora RDS         | Redis    | JSON    |
| `test`    | Integration tests          | Testcontainers     | Disabled | Console |

### Key API Endpoints

| Method | Path                                    | Description              |
|--------|-----------------------------------------|--------------------------|
| GET    | `/api/clients`                          | List clients             |
| POST   | `/api/clients`                          | Create client            |
| GET    | `/api/campaigns`                        | List campaigns           |
| POST   | `/api/campaigns`                        | Create campaign          |
| POST   | `/api/campaigns/{id}/publish`           | Publish campaign         |
| GET    | `/api/creatives/assets`                 | List creative assets     |
| GET    | `/api/insights/clients/{id}/kpis`       | Client KPI summary       |
| GET    | `/api/ai/suggestions`                   | List AI suggestions      |
| POST   | `/api/reports`                          | Generate report          |
| POST   | `/api/meta/connections/start`           | Start Meta OAuth flow    |
| GET    | `/actuator/health`                      | Health check             |

> Full API spec available via Swagger UI at `/swagger-ui.html`

---

## Frontend

### Views

| View             | Route            | Description                         |
|-----------------|------------------|-------------------------------------|
| DashboardView    | `/`              | KPI overview dashboard              |
| ClientsView      | `/clients`       | Client list with create/edit        |
| CreativesView    | `/creatives`     | Assets, packages, copy variants     |
| CampaignsView    | `/campaigns`     | Campaign management with ad builder |
| SuggestionsView  | `/suggestions`   | AI recommendations                  |
| ReportsView      | `/reports`       | Report generation & feedback        |
| MetaView         | `/meta`          | Meta connections & sync status      |
| AuditView        | `/audit`         | Audit log timeline                  |

### Stores (Pinia)

`clientStore`, `campaignStore`, `creativeStore`, `insightStore`, `suggestionStore`, `reportStore`, `metaStore`

---

## Database

### Migrations

| Version | File                            | Description                   |
|---------|---------------------------------|-------------------------------|
| V001    | `V001__init.sql`                | Core schema (agency, clients) |
| V002    | `V002__meta.sql`                | Meta connections & sync jobs  |
| V003    | `V003__creatives.sql`           | Creative assets & packages    |
| V004    | `V004__campaigns.sql`           | Campaigns, adsets, ads        |
| V005    | `V005__ai_reports.sql`          | AI suggestions & reports      |
| V006    | `V006__performance_indices.sql` | 16 performance indices        |

### Seed Data

The `scripts/db-seed.sql` file provides comprehensive development data covering all 8 domain modules with realistic test entries.

---

## Testing

### Test Suite (76 tests)

```bash
cd backend
./mvnw test
```

**Unit Tests (52)** — JUnit 5 + Mockito:
- `ClientServiceTest`, `CampaignServiceTest`, `CreativeServiceTest`
- `AiSuggestionServiceTest`, `ReportServiceTest`, `AuditServiceTest`, `MetaServiceTest`

**Integration Tests (24)** — Testcontainers + PostgreSQL:
- `ClientIntegrationTest` (6 tests)
- `CampaignIntegrationTest` (6 tests)
- `TenantIsolationIntegrationTest` (7 tests)
- `SuggestionIntegrationTest` (3 tests)
- `ReportIntegrationTest` (2 tests)

Integration tests use a **singleton PostgreSQL container** pattern for fast execution with real database validation.

---

## Docker

### Build images

```bash
# Backend
docker build -t amp-api ./backend

# Frontend
docker build -t amp-frontend ./frontend
```

### Run the full stack locally

```bash
docker compose up -d
```

### Image details

| Image        | Base                        | Size   | Health Check               |
|-------------|-----------------------------|--------|----------------------------|
| `amp-api`    | eclipse-temurin:21-jre-alpine | ~200MB | `/actuator/health`         |
| `amp-frontend` | nginx:1.27-alpine          | ~25MB  | `/health`                  |

Both Dockerfiles use **multi-stage builds** with Spring Boot layered JARs (backend) and nginx (frontend).

---

## Infrastructure

### AWS Architecture (Terraform)

Seven modular Terraform configurations in `infra/terraform/modules/`:

| Module       | Resources                                                          |
|-------------|-------------------------------------------------------------------|
| `vpc`        | VPC, 2 public + 2 private subnets, NAT Gateway, ElastiCache Redis |
| `aurora`     | Aurora PostgreSQL 16, Secrets Manager, auto-scaling replicas       |
| `s3`         | Assets bucket (versioned, KMS encrypted) + frontend bucket        |
| `sqs`        | 5 queues with DLQs, 3 EventBridge schedules                      |
| `ecs`        | Fargate cluster, ALB, API + Worker services, IAM roles            |
| `cdn`        | CloudFront distribution with OAC, SPA routing                     |
| `monitoring` | CloudWatch alarms (5xx, DLQ, CPU), dashboard, SNS alerts          |

### Deploy infrastructure

```bash
cd infra/terraform
terraform init
terraform plan -var-file=environments/staging.tfvars
terraform apply -var-file=environments/staging.tfvars
```

---

## CI/CD

### GitHub Actions Workflows

| Workflow         | Trigger                        | Description                          |
|-----------------|--------------------------------|--------------------------------------|
| `backend-ci`    | Push/PR to `main` (backend/)   | Build + test with PostgreSQL service |
| `frontend-ci`   | Push/PR to `main` (frontend/)  | Install + build                      |
| `deploy`         | Push to `main` / manual        | Build images → ECR → deploy to ECS  |

### Required Secrets

| Secret                 | Description                         |
|-----------------------|-------------------------------------|
| `AWS_ACCOUNT_ID`       | AWS account number                  |
| `AWS_DEPLOY_ROLE_ARN`  | IAM role ARN for OIDC federation    |

### Required Variables

| Variable           | Description                        |
|-------------------|------------------------------------|
| `ECS_CLUSTER_NAME` | ECS cluster name for deployment    |

---

## API Documentation

With the backend running locally, visit:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

---

## Development

### Dev Auth Headers

In `local` profile, the `DevAuthFilter` reads tenant context from request headers:

```
X-Agency-Id: <uuid>
X-User-Id: <uuid>
X-User-Role: ADMIN
```

### Environment Variables

Copy `.env.example` to `.env` and fill in your values. See the file for all available options.

### Code Quality

- **Backend**: Spring Boot validation, global exception handler, structured error responses
- **Frontend**: ESLint + OxLint + Prettier, TypeScript strict mode
- **Both**: EditorConfig for consistent formatting

---

## License

Proprietary — All rights reserved.