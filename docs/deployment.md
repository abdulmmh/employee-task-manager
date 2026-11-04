# Deployment guide

The application has been deployed to Netlify, Render (Docker) and Aiven MySQL.
These instructions describe that setup and how to reproduce it. Account secrets,
deployments and repository visibility are managed by the developer.

| Resource | Address |
| --- | --- |
| Frontend | https://etm-mahadi.netlify.app |
| Backend | https://employee-task-manager-api-07j1.onrender.com |
| Backend health | https://employee-task-manager-api-07j1.onrender.com/api/health |

The developer confirmed live role workflows, PDF reports and UptimeRobot frontend/
backend monitors during setup. A new deployment still needs smoke verification.

## 1. Confirm the local release

- Keep Aiven Running; verify the console host/port still match the backend env.
- Run the current backend tests and frontend build/tests; resolve failures.
- Verify all roles, Users/Tasks workflows, download/preview, mobile layout and guards.
- Review and commit feature changes; never add .local-notes/.tools/target/node_modules.
- Push to the private repository manually. Make the repository public for submission.

## 2. Render backend

Create a Web Service connected to the existing GitHub repository. Choose Docker,
branch main, root directory `backend`, Dockerfile `Dockerfile` and a Free instance.
Use manual deploy while preparing the evaluation. Do not add database credentials
to Git or Docker build arguments. The application reads Render's PORT variable.

Set runtime environment variables:

| Name | Value/source |
| --- | --- |
| DB_HOST | Current Aiven connection host |
| DB_PORT | Current Aiven connection port |
| DB_NAME | taskdb |
| DB_USERNAME | etm_app |
| DB_PASSWORD | The restricted application's DB password |
| DB_CA_CERT_B64 | Base64 representation of the Aiven project CA PEM |
| DB_TRUSTSTORE_PASSWORD | A strong truststore password, minimum 6 characters for keytool |
| JWT_SECRET | One persistent Base64 key generated from at least 32 random bytes |
| JWT_EXPIRATION_SECONDS | 1800 unless intentionally configured otherwise |
| JWT_ISSUER | employee-task-manager |
| CORS_ALLOWED_ORIGINS | Exact frontend origin; localhost may be added explicitly for testing |

The entrypoint imports the CA into a temporary PKCS12 truststore as the non-root
application user. It sets DB_TRUSTSTORE_URL automatically; do not copy a Windows
file URL into Render. VERIFY_IDENTITY remains enabled. The CA is public trust
material, but passwords and JWT signing key remain secrets.

To produce the CA value locally, the developer may run in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('D:\Codex-Project\entms\.tools\aiven-ca.pem')) | Set-Clipboard
```

Paste it directly into Render DB_CA_CERT_B64. Do not paste credentials or signing
keys into chat/logs. If creating a new deployment JWT key, use a local secure
generator and paste the result only into the hosting secret setting. Keep it
unchanged across redeploys; rotation invalidates existing tokens.

Deploy and inspect startup logs. Aiven must be reachable during Hibernate schema
validation. The Docker image was built and started successfully on Render with
MySQL connectivity and schema validation. Initial startup took about 136 seconds;
future cold starts can vary. The build stage
downloads Maven dependencies; the runtime image contains only JRE17/app/entrypoint.

## 3. Netlify frontend

Connect the same repository, main branch. Root netlify.toml defines base `frontend`,
publish `dist/employee-task-manager`, Node16.20.2/npm8.19.4 and the SPA redirect.
Keep the generated package-lock committed. Set **API_BASE_URL** in the Netlify
build environment to the actual HTTPS Render backend address ending in `/api`.

The production build configuration script validates and writes only this public
URL into environment.prod.ts. Never supply DB passwords/JWT signing secrets to
the frontend. Local ng serve still uses http://localhost:8080/api.

After obtaining the actual Netlify origin, update Render CORS_ALLOWED_ORIGINS to
that exact scheme/host without a trailing slash. Redeploy/restart the backend,
then verify the frontend login. Keep explicit localhost origin only if needed.

## 4. Live acceptance

- Three-role login, refresh/expiry/logout, protected route refresh and 401 handling.
- ADMIN user search/create/edit/delete; deleted users disappear from the active
  list, lose login access, and retain their task history. Verify last-admin safeguards.
- MANAGER current team options; no cross-team assignment/edit or delete permission.
- EMPLOYEE own status transitions; other-user/task requests403.
- Manager task list and dashboard are own-assigned, PDF report is current-team-wide.
- ADMIN/MANAGER download and preview PDFs; EMPLOYEE report403.
- Mobile navigation/dialogs/table scroll; server error/retry; no leaked credentials.
- Add actual frontend/backend links and final verification status to README.

## 5. Availability and monitoring

Aiven Free can power off for inactivity; no uptime guarantee. One power-off was
observed during development and its precise cause/notification was not established.
Render Free may cold-start. Monitoring cannot automatically power Aiven back on.
Before submission and interview, check Running state, DNS, login, dashboard and
PDF using actual deployed requests.

`GET /api/health` and `HEAD /api/health` are public database-aware probes. They execute `SELECT 1` through
the application's existing verified-TLS connection pool. A successful result
returns HTTP200 with `{"status":"UP"}`; a failed JDBC check returns HTTP503 with
`{"status":"DOWN"}`. Responses are `Cache-Control: no-store` and expose no database
or user details. HEAD runs the same check and returns the same status/headers
without a response body. Other API routes retain their existing authentication rules.
Do not send Authorization headers to this monitor.

The probe sets a three-second JDBC query timeout. Pool acquisition has its own
default Hikari timeout (30 seconds); it is not a three-second end-to-end guarantee.
A database outage does not require intentionally powering off the live service
to test alerts; unit and HTTP tests simulate the failure response.

After deploying and checking the endpoint manually, create an UptimeRobot
HTTP/website monitor for the actual backend URL ending in `/api/health`, using
the Free plan's five-minute interval. The default HEAD method works; use GET if
the plan allows selecting it. Expect HTTP200 and enable the
verified email alert contact for down/up events. A second monitor may check the
frontend root URL. Cold starts may cause temporary failures; inspect Render logs
before interpreting an alert. This probe checks connectivity, not every business
workflow, table, or permission. The developer confirmed both UptimeRobot monitors
working after HEAD support was deployed. Inspect the incident error/status when a
monitor disagrees with a browser request instead of assuming database failure.

## References

- [Render web service configuration](https://render.com/docs/web-services)
- [Netlify dependency versions](https://docs.netlify.com/build/configure-builds/manage-dependencies/)
- [Netlify build environment](https://docs.netlify.com/build/environment-variables/get-started/)
- [Aiven TLS certificates](https://aiven.io/docs/platform/concepts/tls-ssl-certificates)
- [Aiven MySQL Free limitations](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)

The legacy Angular13/Node16 audit limitations remain documented in frontend/README.
Successful local builds do not prove a free-tier deployment is ready.

## Verification checkpoint

Latest developer-run checks: 161 backend tests, 39 frontend tests, all passing;
production frontend build 457.98 kB (November 4, 2026). Backend package refactoring
was followed by clean tests, database startup, login, list, PDF and health checks.
Mocks do not prove real MySQL concurrency behavior. Keep live acceptance separate
from unit/MVC tests and repeat a smoke check after the latest deployment.

For this deployment, Netlify's public build variable is:

```text
API_BASE_URL=https://employee-task-manager-api-07j1.onrender.com/api
```

Render's CORS_ALLOWED_ORIGINS must include exactly `https://etm-mahadi.netlify.app`
(without a trailing slash). Optionally include `http://localhost:4200` explicitly
as a comma-separated second origin for local testing. Do not use a wildcard.
A 401 on a protected URL without a token is expected, but it does not establish
that login, database queries or PDF workflows work.
