# Employee & Task Management System

A full-stack application for employee administration, team task assignment,
progress tracking and PDF reporting. ADMIN, MANAGER and EMPLOYEE have different
permissions enforced by the backend, with matching Angular navigation and guards.

## Live application

| Resource | Link |
| --- | --- |
| Frontend — Netlify | https://etm-mahadi.netlify.app |
| Backend — Render | https://employee-task-manager-api-07j1.onrender.com |
| Database-aware health | https://employee-task-manager-api-07j1.onrender.com/api/health |
| GitHub | https://github.com/abdulmmh/employee-task-manager |

The database is Aiven MySQL 8.4. Free hosting can be slow after inactivity or
temporarily unavailable. Check the health endpoint before the demonstration.
The backend root is protected; open the frontend to use the application.

## Demo accounts

| Role | Email | Password |
| --- | --- | --- |
| ADMIN | `admin@demo.com` | `Admin@123` |
| MANAGER | `manager@demo.com` | `Manager@123` |
| EMPLOYEE | `emp1@demo.com` | `Emp@123` |
| EMPLOYEE | `emp2@demo.com` | `Emp@123` |

Additional seeded managers: `manager2@demo.com`, `manager3@demo.com` with
`Manager@123`; employees `emp3@demo.com` through `emp8@demo.com` use `Emp@123`.
These are demonstration application credentials, not database logins.
The initial dataset contains 12 users and 24 tasks; manual CRUD changes these totals.

## Features and access

| Role | Capabilities |
| --- | --- |
| ADMIN | Create/edit/delete users; assign roles/managers; view/create/edit/delete tasks; global dashboard; PDF of all tasks |
| MANAGER | Assign tasks to own team; view/edit tasks originally assigned by that manager; team-size and own-assigned task statistics; current-team PDF |
| EMPLOYEE | View own tasks and descriptions; advance own status PENDING → IN_PROGRESS → DONE; own task statistics |

- Users: name/email search, pagination, role filter and validated create/edit forms.
- Tasks: pagination, status/priority/assignee filters, plus ADMIN assignee-role filter.
- Searchable, paginated employee/manager pickers; task details; confirmation dialogs;
  success/error toasts; responsive navigation; PDF download and optional preview.
- Stateless JWT login, BCrypt passwords, expiry logout, API interceptor and role guards.
- Login shows waiting guidance after 20 seconds and stops an unanswered attempt
  after 60 seconds, allowing manual retry; credential errors stay inline.

Manager task lists/dashboard show tasks they originally assigned. Their PDF instead
includes tasks of current team EMPLOYEEs from all assigners, including ADMIN.
Report visibility does not grant edit permission. Inactive employees retaining a
manager relationship are included; users whose current role changed from EMPLOYEE
are excluded from that team's PDF. ADMIN retains visibility of all task history.

## Technology and structure

Java 17, Spring Boot 3.5.16, Spring Security JWT, Spring Data JPA/Hibernate,
Jakarta Validation, Lombok, MySQL 8.x and PDFBox 3.0.8. Frontend: Angular 13.3.12,
Angular CLI 13.3.11, TypeScript 4.6.4, RxJS 7.5.7 and Bootstrap 5.3.8.
Build tools: Maven Wrapper 3.9.12 and Node 16.20.2/npm 8.19.4.

```text
backend/       Spring Boot source, tests, Maven Wrapper and Docker deployment
frontend/      Angular source, tests and pinned npm lockfile
docs/          Architecture, deployment, UML/ER diagrams and Postman collection
schema.sql     Initial MySQL DDL and BCrypt-hashed demo seed data
netlify.toml   Frontend build configuration and SPA fallback
```

The backend uses feature packages with controller/service/repository/dto/entity/
enums subpackages where needed. See [architecture](docs/architecture.md) and
[UML Use Case, Class and ER diagrams](docs/diagrams/README.md).

## Local setup

Prerequisites: Git, JDK 17, Node 16.20.2 with npm 8.19.4, MySQL 8.x and a browser.
Chrome is required for the configured frontend headless tests. Maven and a global
Angular CLI do not need separate installation. Dependency downloads need internet.

```powershell
git clone https://github.com/abdulmmh/employee-task-manager.git
cd employee-task-manager
```

Set `JAVA_HOME` to your JDK 17 and select the pinned Node/npm in your terminal.
Verify `java -version`, `node -v` and `npm.cmd -v` before installing dependencies.

### 1. Database

Import [schema.sql](schema.sql) with a schema-administration account into a fresh
MySQL server/database. It creates `taskdb`, `users`, `tasks` and demo data. This is
an initial-install script; do not rerun it over existing tables or live data.
MySQL DDL commits independently, so stop on any import error. The prepared seed
hashes are ready to use; no hash-generation helper is required.

Provision a separate backend DB account, such as `etm_app`, with only SELECT,
INSERT, UPDATE and DELETE on `taskdb.*`; use the administrative account for DDL.
Require encrypted connections. MySQL account credentials are separate from the
application ADMIN role. The application validates the schema and never auto-seeds.

### 2. Backend environment

[backend/.env.example](backend/.env.example) is a reference. Spring Boot does not
automatically read `.env`; export variables in the shell that starts the app.

| Variable | Purpose / default |
| --- | --- |
| `PORT` | HTTP port; default `8080` |
| `DB_HOST`, `DB_PORT` | Required MySQL host and port |
| `DB_NAME` | Database; default `taskdb` |
| `DB_USERNAME` | Restricted account; default `etm_app` |
| `DB_PASSWORD` | Required database account password |
| `DB_TRUSTSTORE_URL` | Required absolute file URI of the local PKCS12 CA truststore |
| `DB_TRUSTSTORE_PASSWORD` | Required truststore password |
| `JWT_SECRET` | Required Base64-encoded key with at least 32 random decoded bytes |
| `JWT_ISSUER` | Default `employee-task-manager` |
| `JWT_EXPIRATION_SECONDS` | Default `1800` (30 minutes) |
| `CORS_ALLOWED_ORIGINS` | Exact comma-separated frontend origins; default `http://localhost:4200` |
| `DB_CA_CERT_B64` | Docker deployment only: Base64 CA PEM; entrypoint generates the truststore |
| `API_BASE_URL` | Frontend build only: public HTTPS backend URL ending in `/api` |

Download the database provider's CA certificate. Create a truststore with the JDK
`keytool`, substituting your own absolute file paths:

```powershell
keytool -importcert -alias aiven-ca -file 'D:\certificates\aiven-ca.pem' -keystore 'D:\certificates\aiven-truststore.p12' -storetype PKCS12
```

Verify the certificate before accepting it. The command asks for the truststore
password. Set `DB_TRUSTSTORE_URL` to, for example,
`file:///D:/certificates/aiven-truststore.p12`. The connection uses VERIFY_IDENTITY
to validate the CA and hostname, without falling back to the system truststore.
Ensure the database host matches its certificate and use its real connection port.

Generate a local signing key without printing it:

```powershell
$etmJwtBytes = New-Object byte[] 32
$etmJwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$etmJwtRandom.GetBytes($etmJwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($etmJwtBytes)
$etmJwtRandom.Dispose()
```

Keep the deployed signing key stable between restarts; rotation invalidates tokens.
After exporting all required DB variables and passwords, run:

```powershell
cd backend
.\mvnw.cmd -v
.\mvnw.cmd spring-boot:run
```

Expect Java 17, Maven 3.9.12 and `Started EtmApplication` on port 8080. Local health:
`http://localhost:8080/api/health`. On Unix use `sh ./mvnw` instead of `mvnw.cmd`.

### 3. Frontend

Open a second terminal at the repository root with Node 16.20.2/npm 8.19.4 selected:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Open `http://localhost:4200`. Development API URL is `http://localhost:8080/api`.
Keep the backend running. The npm scripts use the project's local Angular CLI.
For production API configuration see [deployment](docs/deployment.md).

## API reference

Protected requests require `Authorization: Bearer <token>`. Request DTOs are
validated; responses never expose persistence entities or password hashes.

| Method | Path | Access / purpose |
| --- | --- | --- |
| POST | `/api/auth/login` | Public email/password → `{token, role, name}` |
| POST | `/api/auth/register` | ADMIN user creation alias |
| GET | `/api/users` | ADMIN paginated users/search/filter |
| GET | `/api/users/{id}` | ADMIN user details |
| POST | `/api/users` | ADMIN create; 201 |
| PUT | `/api/users/{id}` | ADMIN edit; 200 |
| DELETE | `/api/users/{id}` | ADMIN soft delete; 204 |
| GET | `/api/tasks` | Role-scoped paginated task list |
| GET | `/api/tasks/{id}` | Same ownership scope |
| POST | `/api/tasks` | ADMIN/MANAGER assign; 201 |
| PUT | `/api/tasks/{id}` | ADMIN or original MANAGER edit |
| PUT | `/api/tasks/{id}/status` | Assigned EMPLOYEE status update |
| DELETE | `/api/tasks/{id}` | ADMIN physical delete; 204 |
| GET | `/api/team/employees` | ADMIN all active EMPLOYEEs; MANAGER own active team options |
| GET | `/api/dashboard/stats` | Authenticated role-specific counts |
| GET | `/api/reports/tasks/pdf` | ADMIN all tasks; MANAGER current-team tasks; PDF attachment |
| GET / HEAD | `/api/health` | Public DB probe: 200 UP / 503 DOWN; HEAD has no body |

Pagination is zero-based: `page=0&size=10`, maximum size 100. Page responses contain
`content`, `page`, `size`, `totalElements`, `totalPages`. Users are ordered by ID
descending (newest first); successful UI creation reloads page zero while editing
preserves the current page. Existing search/role filters remain applied.
Users accept `search`
(name/email), `role`, `active`. Tasks accept `status`, `priority`, `assignee`,
`role` (assignee's current role). Filters only narrow the authenticated scope.
Team options support name `search`. The UI shows active users and active assignee
choices; historical/inactive task assignees remain queryable through the API.

Create user: `fullName`, `email`, `password`, `role`, `managerId` (EMPLOYEE only).
Update also requires `active`; omitted/null/blank edit password preserves its hash.
Create/edit task: `title`, `description`, `priority`, `deadline` (ISO date),
`assignedTo` (employee ID). New status is PENDING and assigner is server-derived.
Status requests use `{"status":"IN_PROGRESS"}` then `{"status":"DONE"}`.

Errors contain `timestamp`, `status`, `message`, `fieldErrors`: 400 invalid input,
401 missing/invalid authentication or credentials, 403 forbidden access, 404 missing
record, 409 business conflict and 503 service unavailability.

## Business safeguards

- User DELETE sets `active=false`, revoking future authenticated access while
  preserving task history. UI removes the account from its active list. Backend
  administrative updates can restore eligible accounts; the UI has no restore control.
- The final active ADMIN cannot be deleted/demoted. Managers must reassign their
  active employees before deactivation/demotion. Additional ADMINs are created only
  by an existing ADMIN; no public registration exists.
- Unfinished assigned-to/by tasks block role changes. An employee transfer requires
  unfinished assignments resolved. Completed history is retained.
- Only PENDING tasks can be reassigned. Original assignedBy is preserved; a task
  originating from a manager remains within that manager's team when reassigned.
- Only the assigned EMPLOYEE advances status; skipping/reversing returns 409,
  repeating the current status is idempotent. ADMIN/MANAGER cannot update status.
- Current DB role/active state is checked for each authenticated request. Password
  resets do not revoke an already-issued JWT; access tokens expire after 30 minutes
  by default. There is no refresh-token/server-side logout revocation mechanism.

## Verification and Postman

Latest developer-run results: **164 backend tests pass**, **41 frontend tests pass**,
and production frontend build **458.03 kB** (November 4, 2026). Local/database/live
role workflows, PDF reports and health monitoring were also checked during development.
Any later deployment still needs its own smoke check.

```powershell
# From backend/ with Java 17
.\mvnw.cmd clean test

# From frontend/ with Node 16.20.2/npm 8.19.4
npm.cmd run build
npm.cmd run test:ci
```

Backend tests use mocks/MVC slices and do not establish real MySQL concurrency
behavior or production availability. Package changes require a clean build to
remove stale compiled classes. Administrative/task writes share a database lock
to serialize business-rule checks; this demo prioritizes consistency over throughput.

Import [Postman collection](docs/postman/employee-task-manager.postman_collection.json).
Set collection `baseUrl` to `http://localhost:8080` or the deployed backend origin
(without `/api`). Run the primary and additional role logins; successful responses
automatically save fresh collection tokens. For localhost use Postman desktop app/
Desktop Agent. The user workflow creates unique test emails and cleans up its new
accounts with soft deletion; the task workflow deletes its new task. Run in order;
manual tests change data. Do not export real tokens into committed collections.
See the [Postman guide](docs/postman/README.md) for variables, data assumptions and
execution instructions for the 55 requests.

## Deployment, diagrams and media

- [Render, Netlify, TLS and monitoring setup](docs/deployment.md)
- [Backend package architecture](docs/architecture.md)
- [UML Use Case, Class and ER diagrams — SVG/PNG and editable sources](docs/diagrams/README.md)

[View the application screenshots](docs/screenshots/README.md). The final demo-video link will be added after media review.

## Known limitations

- The required Angular 13/Node 16 stack is legacy. The recorded npm audit has 59
  findings (5 low, 30 moderate, 22 high, 2 critical), including runtime and build
  dependencies; these are unresolved. A working build is not a security clearance.
- PDF standard fonts support English/WinAnsi; unsupported Bengali/emoji glyphs are
  replaced with `?`. Reports are generated in memory for the small demo dataset.
- Report IDs are actual task IDs, so gaps are expected; generated times are UTC.
- Free Render/Aiven availability is not guaranteed. UptimeRobot detects failures
  but cannot power Aiven on. A successful SELECT 1 health probe does not verify all
  application workflows. A cold start may exceed the 60-second login timeout;
  retry may be necessary even after the server begins starting.
