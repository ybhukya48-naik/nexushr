# NexusHR Go-Live Checklist

Use this checklist before every production deployment.

## A) Pre-Deploy (Code + Config)
- [ ] Backend build is green (`mvn -DskipTests compile`).
- [ ] Frontend build is green.
- [ ] `SPRING_PROFILES_ACTIVE=prod` is set in backend host.
- [ ] `DB_URL`, `DB_USER`, `DB_PASSWORD` are correct for Neon production DB.
- [ ] `JWT_SECRET` is configured (strong secret, not default).
- [ ] `CORS_ALLOWED_ORIGINS=https://hr.cyond.com` is set.
- [ ] `SWAGGER_ENABLED=false` in production.
- [ ] `DEMO_USERS_ENABLED` value is intentional (`true` for demo logins, `false` for strict real users).

## B) Platform Safety
- [ ] Backend host plan is Always On (no sleep/cold start).
- [ ] Backend health check endpoint is `/actuator/health`.
- [ ] Auto deploy is enabled from the correct branch.
- [ ] Restart policy is enabled on backend host.
- [ ] SSL certificates are valid for frontend and API domains.

## C) Database Safety
- [ ] Neon production branch/database is selected.
- [ ] Connection pooler endpoint is used in `DB_URL`.
- [ ] DB user has required permissions.
- [ ] Backup/restore path is documented and tested.

## D) Smoke Tests After Deploy
- [ ] Open https://hr.cyond.com and confirm page loads.
- [ ] Open https://hr-api.cyond.com/actuator/health and confirm `"status":"UP"`.
- [ ] CORS preflight from frontend origin succeeds.
- [ ] Login works for at least one HR user.
- [ ] Login works for at least one employee user.
- [ ] 1 protected API call succeeds with JWT token.

## E) Performance Check
- [ ] Frontend first load is acceptable (no extreme delay).
- [ ] Login API response is acceptable.
- [ ] No recurring DB timeout errors in backend logs.

## F) Rollback Readiness
- [ ] Previous backend deploy version is available for rollback.
- [ ] Previous frontend deploy version is available for rollback.
- [ ] Team knows who will trigger rollback and how.

## G) Monitoring and Alerts
- [ ] Uptime monitor configured for `https://hr.cyond.com`.
- [ ] Uptime monitor configured for `https://hr-api.cyond.com/actuator/health`.
- [ ] Alert recipients include at least 2 maintainers.
- [ ] Alert threshold is set (for example: 2 consecutive failures).

## H) Final Go/No-Go
- [ ] All above items are checked.
- [ ] Deployment owner approves go-live.
- [ ] Release timestamp and owner recorded.

---

Release Record:
- Date/Time:
- Release Owner:
- Backend Version:
- Frontend Version:
- Notes:
