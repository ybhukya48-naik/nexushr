# NexusHR production deployment preparation

**Hold: do not deploy or change the production database until an authorized
operator confirms access, backups, restore procedures, staging tests and
approval.** This is a proposed procedure, not evidence that production has
been updated. As of November 2026, `hr.cyond.com` resolves to an nginx server.
Render service `nexushr-1` serves `hr-api.cyond.com` and tracks `main`. The
frontend server owner, production database backup and restore status remain
unverified.
The repository CD workflow does not deploy to that server. Vercel previews
are not the live site.

## Release gates

- Identify the real nginx host, backend service, PostgreSQL database, current
  app versions, Flyway history, deployment owner and secure access method.
  Never share or store credentials, tokens, private keys or database dumps in
  chat or in the repository.
- Establish and test a backup and recovery plan on an isolated database.
  Production backup existence and restorability are currently **unconfirmed**.
- Backend and frontend CI passed on PR head `990953b`. Re-run checks on the
  actual release commit, rehearse migrations on a restored production copy,
  run concurrency tests, and perform authenticated end-to-end testing in
  staging for both companies and all roles.
- Confirm treatment of overnight open lunches and operational response to
  failed inactivity sweeps. Browser heartbeats detect connectivity to the app,
  not actual desktop activity. Sleeping devices, network loss and browser
  throttling can cause checkout; a connected open tab can continue heartbeats.
  Lunch pauses inactivity checkout and can remain open overnight.
  Employees must end lunch manually before checking out.
- Agree a maintenance window, release and rollback owners, monitoring, and
  acceptance criteria with payroll/HR. Confirm that no other process applies
  Flyway migrations at the same time.

## Build and configuration

Build the reviewed release commit using existing dependencies and CI:

```text
backend:          mvn verify
frontend:         npm ci
frontend:         npm run lint
frontend:         npm run build
```

`mvn verify` includes PostgreSQL/Testcontainers integration tests and needs a
working Docker daemon. A successful `mvn -DskipTests package` builds the Spring
Boot jar but does **not** substitute for tests. The frontend build produces
`nexushr-frontend/dist`; the production backend jar is
`backend/target/nexushr-backend-0.1.0.jar`.

The backend production profile uses the following environment variable names:

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `prod`. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Verified PostgreSQL JDBC connection, supplied through the host's protected secret mechanism. Explicitly set `DB_URL`; do not rely on the historical default. |
| `JWT_SECRET` | Existing strong signing secret from the secret manager; changing it invalidates existing tokens. |
| `CORS_ALLOWED_ORIGINS` | Exact approved HTTPS origins, comma-separated. |
| `SERVER_ADDRESS`, `PORT` | Actual listening address and port; loopback and 8080 are defaults for a backend on the nginx host. On Render, set `SERVER_ADDRESS` to `0.0.0.0` and use the Render-provided port. Verify these on staging first. |
| `DEMO_USERS_ENABLED`, `SWAGGER_ENABLED` | Confirm production policy; defaults in `prod` are false. |
| `DB_MAX_POOL_SIZE`, `DB_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS` | Optional database pool tuning. |

Use the existing secure service configuration; do not put values in this
document, source control, shell history, screenshots or chat. Confirm other
integrations and mail settings against the actual installation. The frontend
uses same-origin `/api/v1` on `hr.cyond.com`; `VITE_API_BASE_URL` is used for
other hosts, and is embedded at build time, not a secret.

The November 25 Render deploy of `06ce520` exited during startup because
the database returned SQLSTATE `53000`, reporting that the account or project
exceeded its quota. Flyway could not obtain a connection; the log does not show
a V43 or V44 migration failure. The database owner must resolve the quota and
securely verify connectivity. Render's listed service variables did not include
`SERVER_ADDRESS`, so the loopback default is an independent networking risk
after database startup succeeds. Do not retry production until release gates
are met.

## Migration and backup

Flyway is enabled at backend startup and Hibernate validates the schema. The
versioned scripts are
[`V43__manual_lunch_attendance.sql`](src/main/resources/db/migration/V43__manual_lunch_attendance.sql)
and [`V44__employee_annual_ctc.sql`](src/main/resources/db/migration/V44__employee_annual_ctc.sql).
Do not execute them manually and then let Flyway rerun them; this would leave
Flyway history inconsistent. Before startup, an authorized database operator
must check `flyway_schema_history` and compare every pending version and
checksum, including any migrations before V43, with the release. Stop if a
version conflicts, a checksum differs, or the database is not the expected one.

V43 adds manual lunch state; old rows retain the legacy policy. V44 adds a
nullable employee annual CTC without backfilling or reinterpreting existing
salary. Legacy `baseSalary` remains a monthly fallback until HR sets an annual
CTC. Monthly payroll uses annual CTC divided by 12, proportional to eligible
recorded work and up to one approved CASUAL day per month; two half-days count
as one day. Only scheduled dates through today are credited. On a leave date,
credited leave and worked time together cannot exceed eight hours; the approved
leave consumes its full allowance even if attendance reduces the credit. EARNED
leave is not paid under this policy. Overtime is displayed separately and is not included in CTC
earnings. A payroll entry's override affects that record only; existing payroll
snapshots and paid records are preserved during bulk recalculation.
The payroll employee preview shows today's eligible work and its estimated
gross share of monthly CTC before deductions and incentives. Monthly caps,
paid leave, and rounding can change the final payroll total.
Managers can view their own attendance history and payslips and submit their
own leave requests to HR. Managers see same-company direct reports, not HR
administration or leave approvals. The manager team report exposes recorded
work, overtime, leave status and payroll status, but not salary amounts or
leave reasons. A manager's direct reports must be assigned through Reporting
Manager; sharing a department alone does not grant access. HR/admin receive
leave approval notifications.
The employee form supports teams A-G or a custom department; HR must also
select the reporting manager for the manager's team view. HR, admin and super
admin have their own attendance controls on the dashboard.

1. Arrange a consistent backup immediately before deployment with the database
   owner. Use the provider's point-in-time recovery where available and a
   PostgreSQL custom-format dump where permitted. For example, with a
   preconfigured protected libpq service, an authorized operator can run
   `pg_dump --format=custom --file=<secure-backup-path> --dbname=service=nexushr-production`.
   Substitute an approved path and service name; keep connection details out of
   command history. Ensure encryption, restricted access, retention, integrity
   and backup time are recorded securely. Include relevant roles and privileges
   in the provider's recovery plan.
2. Verify the archive using `pg_restore --list <secure-backup-path>`, then
   restore to an isolated non-production database using the provider's approved
   method. Compare schema, Flyway history, row counts and representative
   attendance/payroll data. A listing alone is not a restore test. Sanitize
   data and restrict access before using it outside production.
3. Rehearse all pending Flyway migrations and application startup on that
   isolated copy, test both companies and roles, and record results. Confirm
   the production account has required migration permissions and the version
   of PostgreSQL matches staging. Do not repair Flyway history or alter salary
   values to force a migration through.
4. At the approved window, stop or coordinate writers, take and confirm the
   final backup, deploy the backend with the agreed service manager, and allow
   Flyway to migrate once. Inspect logs and Flyway history before making the
   new frontend available. If anything is unexpected, stop and follow rollback.

## Nginx integration

Inspect the existing server configuration (`nginx -T` through the operator's
approved access) and preserve its TLS certificates, redirects, headers, access
controls and other services. The repository's `infra/docker/nginx.conf` is a
container example, not proof of the live configuration.

The following is an illustrative fragment **inside the existing HTTPS server
block**, not a complete replacement. Verify paths, upstream and upload limits
against the actual host; validate with `nginx -t` before an approved reload.
The proxy preserves the `/api/` prefix. Do not expose the backend directly.

```nginx
root /srv/nexushr/current/frontend;
index index.html;
client_max_body_size 12m;

location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location / {
    try_files $uri $uri/ /index.html;
}
```

The backend's prod multipart request limit is 12 MB. If monitoring needs
`/actuator/health`, proxy it separately and restrict access as appropriate.
Ensure `index.html` is not served from an obsolete cache; versioned Vite
assets may be cached only while their release remains available. Keep the
previous frontend and backend artifacts for rollback.

## Post-deployment verification and rollback

After authorized deployment, verify TLS, SPA navigation, same-origin API and
health using the existing monitoring method. With authorized test accounts,
check login errors and employee-owned emails, HR/admin/manager attendance,
manual lunch, overtime, 30-minute heartbeat cutoff, manager team scope, CTC
entry and attendance-based payroll for both companies. Verify actual database
records, not only UI messages; do not generate or modify real payroll merely
for a smoke test. Review logs, migration history and alerts. Monitor afterward.

If verification fails, stop new writes and notify the release owner. Revert
the application artifacts and nginx routing to their known-good versions only
after testing backward compatibility with the migrated schema. Additive V43
and V44 columns may remain for an application rollback; do not drop columns or
edit Flyway history as a shortcut. Restoring a database is destructive and can
discard post-backup transactions: the database owner must assess, preserve and
reconcile subsequent writes, approve a recovery point, and perform and verify
the provider-specific restore. Keep the site in maintenance mode until the
application, data and payroll are reconciled and approved.
