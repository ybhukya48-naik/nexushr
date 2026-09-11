# NexusHR â€” AI-Enabled Enterprise HR & Workforce Intelligence Platform

Production-grade Java full-stack HRMS covering the complete employee lifecycle, with AI attrition insights, role-based access control, and a full observability stack.

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25 Â· Spring Boot 3.5.9 Â· Spring Security 6 Â· JPA + Hibernate 6 Â· Flyway |
| Database | PostgreSQL 17 (prod) Â· H2 (tests) |
| Cache | Redis 7 |
| Auth | Stateless JWT (JJWT 0.12) |
| Frontend | React 19 Â· TypeScript Â· Vite Â· react-router-dom |
| API Docs | springdoc-openapi (Swagger UI) |
| CI/CD | GitHub Actions â€” build/test/push/deploy |
| Containers | Docker multi-stage Â· nginx:1.27 |
| Orchestration | Kubernetes manifests Â· Helm chart |
| Observability | Prometheus 3 Â· Grafana 12 Â· Spring Boot Actuator |
| Testing | JUnit 5 Â· Mockito Â· TestContainers (PostgreSQL) Â· JaCoCo |

## Repository Layout

```
â”œâ”€â”€ backend/                   Spring Boot API
â”‚   â””â”€â”€ src/test/              65 unit tests + 19 integration tests (TestContainers)
â”œâ”€â”€ nexushr-frontend/         React SPA
â”‚   â””â”€â”€ src/
â”‚       â”œâ”€â”€ api/client.ts      Typed API client (all 14 endpoints)
â”‚       â”œâ”€â”€ components/        Nav, PageShell
â”‚       â””â”€â”€ pages/             Login, Dashboard, Employees, Leave,
â”‚                              Attendance, Payroll, Performance, AI Insights
â”œâ”€â”€ infra/
â”‚   â”œâ”€â”€ docker/                backend.Dockerfile Â· frontend.Dockerfile Â· nginx.conf
â”‚   â”œâ”€â”€ k8s/                   namespace Â· deployments Â· services Â· ingress Â· HPA
â”‚   â”œâ”€â”€ helm/nexushr/          Helm chart
â”‚   â””â”€â”€ monitoring/            prometheus.yml Â· Grafana datasource + dashboard
â”œâ”€â”€ .github/workflows/
â”‚   â”œâ”€â”€ ci.yml                 build Â· test (Java 25) Â· JaCoCo coverage Â· tsc Â· Vite build
â”‚   â””â”€â”€ cd.yml                 Docker push (ghcr.io) Â· Helm deploy Â· rollout verify
â””â”€â”€ docker-compose.yml         Full local stack: postgres Â· redis Â· backend Â· frontend
                               Â· prometheus Â· grafana
```

## Quick Start

### Prerequisites
- Docker Desktop â‰¥ 4.x
- (Optional) JDK 25 + Maven 3.9 for local backend dev

### 1 â€” Start the full stack

```bash
docker compose up --build
```

Services start in dependency order (postgres â†’ redis â†’ backend â†’ frontend + prometheus â†’ grafana).

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

### 2 â€” Demo users

| Username | Role | Access |
|----------|------|--------|
| `admin` | ADMIN | Everything |
| `hr` | HR | All except ADMIN-only |
| `manager` | MANAGER | Dashboard, Employees, Leave, Attendance, Performance, AI |
| `employee` | EMPLOYEE | Dashboard, own Leave & Attendance |

Password: any non-empty value (the bootstrap auth assigns roles by username).

### 3 â€” Run tests

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

- All secrets are injected via environment variables / k8s Secrets â€” never hardcoded
- Pods run as non-root (`runAsUser: 1000`), with `readOnlyRootFilesystem: true` and all Linux capabilities dropped
- Network policies default-deny all ingress; only frontendâ†’backend (8080) and ingressâ†’frontend (80) are allowed
- JWT expiry: 120 minutes (configurable via `app.jwt.expiration-minutes`)
- CSRF disabled (stateless JWT); CORS configured via Spring defaults

## Roadmap

- Replace stub auth with a real user store + BCrypt password hashing + MFA
- Add OpenTelemetry distributed tracing (Jaeger / Tempo)
- Add Spring AI integration for a real LLM-backed attrition recommendation
- Add notification microservice (email / in-app)
- Add Playwright E2E tests for the frontend
- Add k6 load tests (target: 10 k concurrent users)
