# Deployment guide

Deployment is pending. No live URL below is claimed to exist. The developer runs
tests/builds, commits/pushes and performs all account/deployment actions manually.

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
validation. Free-tier cold start/memory limits require a real deployed check;
the Docker image has not yet been built/run in this project. The build stage
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

`GET /api/health` is a public database-aware probe. It executes `SELECT 1` through
the application's existing verified-TLS connection pool. A successful result
returns HTTP200 with `{"status":"UP"}`; a failed JDBC check returns HTTP503 with
`{"status":"DOWN"}`. Responses are `Cache-Control: no-store` and expose no database
or user details. Other API routes retain their existing authentication rules.
Do not send Authorization headers to this monitor.

The probe sets a three-second JDBC query timeout. Pool acquisition has its own
default Hikari timeout (30 seconds); it is not a three-second end-to-end guarantee.
A database outage does not require intentionally powering off the live service
to test alerts; unit and HTTP tests simulate the failure response.

After deploying and checking the endpoint manually, create an UptimeRobot
HTTP/website monitor for the actual backend URL ending in `/api/health`, using
GET and the Free plan's five-minute interval. Expect HTTP200 and enable the
verified email alert contact for down/up events. A second monitor may check the
frontend root URL. Cold starts may cause temporary failures; inspect Render logs
before interpreting an alert. This probe checks connectivity, not every business
workflow, table, or permission. Monitor setup is pending until verified in UptimeRobot.

## References

- [Render web service configuration](https://render.com/docs/web-services)
- [Netlify dependency versions](https://docs.netlify.com/build/configure-builds/manage-dependencies/)
- [Netlify build environment](https://docs.netlify.com/build/environment-variables/get-started/)
- [Aiven TLS certificates](https://aiven.io/docs/platform/concepts/tls-ssl-certificates)
- [Aiven MySQL Free limitations](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)

The legacy Angular13/Node16 audit limitations remain documented in frontend/README.
Successful local builds do not prove a free-tier deployment is ready.

## Verification checkpoint

Before deployment configuration was added, the developer verified 152 backend
tests and 28 frontend tests with no failures. The latest production frontend
build was 456.46 kB. These results do not validate the Docker image or Netlify's
build-time API configuration; both must be checked during the first deployment.

For the first Render deployment, set CORS_ALLOWED_ORIGINS to
`http://localhost:4200`. Once Netlify provides the final frontend origin, replace
this value with that origin (or an explicit comma-separated list if local
testing is still needed). Never use a wildcard. A 401 response from a protected
backend URL without a token is expected; it is not a successful login check.
