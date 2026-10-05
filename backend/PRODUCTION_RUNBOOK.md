# NexusHR Production Runbook

## 1) Architecture (Current)
- Frontend host: Vercel
- Frontend domain: https://hr.cyond.com
- Backend host: Render
- API domain: https://hr-api.cyond.com
- Database: Neon Postgres

## 2) Production Environment Variables (Backend on Render)
Set these in Render service environment:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL=jdbc:postgresql://ep-red-haze-b5nyszma-pooler.c-7.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require`
- `DB_USER=<your_neon_user>`
- `DB_PASSWORD=<your_neon_password>`
- `JWT_SECRET=<strong_random_secret_32+chars>`
- `CORS_ALLOWED_ORIGINS=https://hr.cyond.com`
- `DEMO_USERS_ENABLED=true` (set `false` only when fully real-user auth is ready)
- `SWAGGER_ENABLED=false`

Optional pool tuning:
- `DB_MAX_POOL_SIZE=10`
- `DB_MIN_IDLE=1`
- `DB_CONNECTION_TIMEOUT_MS=10000`
- `DB_VALIDATION_TIMEOUT_MS=5000`

## 3) Render Service Settings
- Plan: Use an Always On plan (avoid free sleep/cold starts).
- Health check path: `/actuator/health`
- Auto deploy: Enabled from main branch.
- Restart policy: Enabled.

## 4) Vercel Settings
- Production domain mapped: `hr.cyond.com`
- Frontend API base URL points to: `https://hr-api.cyond.com`
- SSL: auto-managed by Vercel (keep DNS records unchanged).

## 5) Neon Settings
- Use a dedicated production branch/database.
- Confirm DB credentials are correct in Render.
- Ensure connection pooling endpoint is used (current URL is pooler endpoint).
- Keep one DB backup/restore plan documented in Neon.

## 6) First-Level Verification Commands
Run from any machine with internet:

```powershell
# Frontend check
Invoke-WebRequest -Uri "https://hr.cyond.com/" -UseBasicParsing

# Backend health check
Invoke-WebRequest -Uri "https://hr-api.cyond.com/actuator/health" -UseBasicParsing

# CORS preflight check from frontend origin
$headers = @{
  "Origin"="https://hr.cyond.com"
  "Access-Control-Request-Method"="POST"
  "Access-Control-Request-Headers"="content-type,authorization"
}
Invoke-WebRequest -Uri "https://hr-api.cyond.com/api/v1/auth/login" -Method Options -Headers $headers -UseBasicParsing
```

Expected:
- Frontend: HTTP 200
- Health: `{"status":"UP"...}`
- CORS: `Access-Control-Allow-Origin: https://hr.cyond.com`

## 7) Login Troubleshooting (401)
If HR/employee login fails:
- Check backend health endpoint first.
- Confirm `DB_URL`, `DB_USER`, `DB_PASSWORD` in Render are correct.
- Confirm user exists in Neon employees table.
- Confirm user is active and lifecycle status is ACTIVE.
- Confirm password is bcrypt-hashed and matches entered password.
- If using demo login (`admin/hr/manager/employee`), ensure `DEMO_USERS_ENABLED=true`.

## 8) Slow Load Troubleshooting
- If frontend is fast but login is slow, check backend plan for cold starts.
- Check Neon latency/limits and Render logs for DB connection timeouts.
- Confirm CORS origin exactly matches `https://hr.cyond.com`.

## 9) Rollback Plan
If latest deployment is unstable:
- Render: rollback to previous successful deploy.
- Vercel: promote previous successful deployment.
- Keep DB schema compatible before rollback.
- Re-run section 6 verification checks.

## 10) Monitoring and Alerts
Set uptime alerts for:
- `https://hr.cyond.com`
- `https://hr-api.cyond.com/actuator/health`

Recommended alert policy:
- Alert after 2 consecutive failures (1-minute interval).
- Notify at least 2 maintainers.

## 11) Operational Rule
Public site availability must not depend on local desktop Docker.
If local machine is OFF, production URLs must still pass section 6 checks.
