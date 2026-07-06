# NexusHR — AI-Enabled Enterprise HR & Workforce Intelligence Platform

Production-grade Java full-stack HRMS covering the complete employee lifecycle, with AI attrition insights, role-based access control, and a full observability stack.

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25 · Spring Boot 3.5.9 · Spring Security 6 · JPA + Hibernate 6 · Flyway |
| Database | PostgreSQL 17 (prod) · H2 (tests) |
| Cache | Redis 7 |
| Auth | Stateless JWT (JJWT 0.12) |
| Frontend | React 19 · TypeScript · Vite · react-router-dom |
| API Docs | springdoc-openapi (Swagger UI) |
| CI/CD | GitHub Actions — build/test/push/deploy |
| Containers | Docker multi-stage · nginx:1.27 |
| Orchestration | Kubernetes manifests · Helm chart |
| Observability | Prometheus 3 · Grafana 12 · Spring Boot Actuator |
| Testing | JUnit 5 · Mockito · TestContainers (PostgreSQL) · JaCoCo |

## Repository Layout

```
├── backend/                   Spring Boot API
│   └── src/test/              65 unit tests + 19 integration tests (TestContainers)
├── frontend/                  React SPA
│   └── src/
│       ├── api/client.ts      Typed API client (all 14 endpoints)
│       ├── components/        Nav, PageShell
│       └── pages/             Login, Dashboard, Employees, Leave,
│                              Attendance, Payroll, Performance, AI Insights
├── infra/
│   ├── docker/                backend.Dockerfile · frontend.Dockerfile · nginx.conf
│   ├── k8s/                   namespace · deployments · services · ingress · HPA
│   ├── helm/nexushr/          Helm chart
│   └── monitoring/            prometheus.yml · Grafana datasource + dashboard
├── .github/workflows/
│   ├── ci.yml                 build · test (Java 25) · JaCoCo coverage · tsc · Vite build
│   └── cd.yml                 Docker push (ghcr.io) · Helm deploy · rollout verify
└── docker-compose.yml         Full local stack: postgres · redis · backend · frontend
                               · prometheus · grafana
```

## Quick Start

### Prerequisites
- Docker Desktop ≥ 4.x
- (Optional) JDK 25 + Maven 3.9 for local backend dev

### 1 — Start the full stack

```bash
docker compose up --build
```

Services start in dependency order (postgres → redis → backend → frontend + prometheus → grafana).

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

### 2 — Demo users

| Username | Role | Access |
|----------|------|--------|
| `admin` | ADMIN | Everything |
| `hr` | HR | All except ADMIN-only |
| `manager` | MANAGER | Dashboard, Employees, Leave, Attendance, Performance, AI |
| `employee` | EMPLOYEE | Dashboard, own Leave & Attendance |

Password: any non-empty value (the bootstrap auth assigns roles by username).

### 3 — Run tests

```bash
# Unit tests only (no Docker needed)
cd backend
mvn test

# Unit + integration tests (Docker required for TestContainers)
mvn verify
```

**Current coverage: 88.4% line / 88.9% branch** (JaCoCo report: `backend/target/site/jacoco/`)

## API Reference

Full interactive docs at `/swagger-ui.html`. Authenticate by calling `POST /api/v1/auth/login`, then click **Authorize** in Swagger UI and paste the `accessToken`.

| Method | Path | Role |
|--------|------|------|
| POST | `/api/v1/auth/login` | Public |
| GET/POST | `/api/v1/employees` | Any authenticated |
| GET/POST | `/api/v1/leaves` | Any authenticated |
| PATCH | `/api/v1/leaves/{id}/status` | HR / ADMIN |
| GET/POST | `/api/v1/attendance` | Any authenticated |
| GET/POST | `/api/v1/payroll` | Any authenticated |
| GET/POST | `/api/v1/performance` | Any authenticated |
| GET | `/api/v1/dashboard/summary` | HR / ADMIN / MANAGER |
| GET | `/api/v1/ai/attrition/{employeeId}` | Any authenticated |

## Observability

The backend exposes metrics at `/actuator/prometheus`. A pre-built Grafana dashboard (**NexusHR Backend**) provisions automatically and shows:

- HTTP request rate & error rate (5xx)
- P99 latency per endpoint
- JVM heap + non-heap usage
- HikariCP connection pool (active / idle / pending)
- GC pause time
- CPU usage

## Kubernetes Deployment

```bash
# Create namespace + network policies + quotas
kubectl apply -f infra/k8s/namespace.yaml

# Deploy (or use Helm)
kubectl apply -f infra/k8s/

# Or with Helm
helm upgrade --install nexushr infra/helm/nexushr \
  --namespace nexushr --create-namespace \
  --set backend.image=ghcr.io/<owner>/nexushr-backend:<tag> \
  --set frontend.image=ghcr.io/<owner>/nexushr-frontend:<tag>
```

Required k8s Secret:
```bash
kubectl create secret generic nexushr-secrets -n nexushr \
  --from-literal=db-url='jdbc:postgresql://postgres:5432/nexushr' \
  --from-literal=db-user='nexushr' \
  --from-literal=db-password='<password>' \
  --from-literal=jwt-secret='<min-32-char-secret>'
```

## Security Notes

- All secrets are injected via environment variables / k8s Secrets — never hardcoded
- Pods run as non-root (`runAsUser: 1000`), with `readOnlyRootFilesystem: true` and all Linux capabilities dropped
- Network policies default-deny all ingress; only frontend→backend (8080) and ingress→frontend (80) are allowed
- JWT expiry: 120 minutes (configurable via `app.jwt.expiration-minutes`)
- CSRF disabled (stateless JWT); CORS configured via Spring defaults

## Roadmap

- Replace stub auth with a real user store + BCrypt password hashing + MFA
- Add OpenTelemetry distributed tracing (Jaeger / Tempo)
- Add Spring AI integration for a real LLM-backed attrition recommendation
- Add notification microservice (email / in-app)
- Add Playwright E2E tests for the frontend
- Add k6 load tests (target: 10 k concurrent users)

