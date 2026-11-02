# Employee & Task Management System

A full-stack web application for managing employees and tasks with Admin, Manager,
and Employee roles.

## Current status

The backend starter and MySQL schema are in place. The demo dataset has been
prepared and manually imported: 12 users and 24 tasks across three teams.
JPA entities and environment-based database configuration are implemented. Local
backend startup with the configured database connection and schema validation has
been verified. Database-backed JWT login and an admin-only user detail endpoint
are implemented; all 34 automated authentication/security tests pass. Live login
and protected-user endpoint checks have been verified: admin access returns 200,
manager/employee access returns 403, and missing authentication returns 401.
Admin user creation, editing, pagination/search, soft deletion, and safeguards are
implemented; all 72 user/auth/security tests passed in the user-run suite. Live
user list/create/edit/password-preservation/deactivation/login checks also passed.
Task CRUD, ownership-scoped pagination/filters and employee status transitions are
implemented; the user-run suite passed all 117 tests. Live task creation/status
and cross-team/delete permission checks also passed. Dashboard aggregates are
implemented; the user-run suite passed all 127 tests and dashboard live checks
passed for all three roles and missing authentication. PDF report code and its
tests passed in the user-run 142-test suite. The generated four-page preview was
visually checked for table layout, repeated headers and page numbers. Live report
download and database scope checks remain pending.
The Angular frontend remains to be built.

## Technology

- Backend: Java 17, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Hibernate,
  Jakarta Validation, and Lombok.
- Database: MySQL 8.x.
- Frontend (planned): Angular 13 and Bootstrap 5.
- Build: Maven Wrapper, pinned to Apache Maven 3.9.12.

## Repository structure

```text
backend/
  .mvn/wrapper/        Maven Wrapper configuration
  src/main/java/       Java application source
  src/main/resources/  Application configuration
  mvnw                 Unix Maven Wrapper
  mvnw.cmd             Windows Maven Wrapper
  pom.xml              Dependencies and build configuration
schema.sql             MySQL table definitions and demo seed data
```

The Java base package is `io.github.abdulmmh.etm`. Feature packages will be added
under it as the corresponding features are implemented.

## Verify the backend starter

Install a JDK 17 distribution and set `JAVA_HOME` to its root directory. A separate
Maven installation is not required. The Wrapper downloads its pinned Maven
distribution on first use; dependency downloads also require internet access.

From PowerShell:

```powershell
cd backend
.\mvnw.cmd -v
.\mvnw.cmd compile
```

From a Unix shell:

```sh
cd backend
sh ./mvnw -v
sh ./mvnw compile
```

The version command should report Maven 3.9.12 and Java 17. A successful compile
ends with `BUILD SUCCESS` and writes generated files to `backend/target/`.

Compilation verifies dependency resolution and Java source. It does not verify
database connectivity or application startup. Run authentication/security tests
with `./mvnw test` (Windows: `.\mvnw.cmd test`). These use mocked repositories and
do not connect to the cloud database.

## Database setup

Use MySQL 8.x and a schema-administration account to import the root `schema.sql`
on a fresh database. The script includes ready-to-import BCrypt password hashes
and creates `taskdb`, `users`, and `tasks`.
It contains no database connection credentials or MySQL account provisioning.

This is an initial installation script: existing tables are not dropped or
overwritten. MySQL DDL commits independently, so stop on an import error before
continuing. Successful import of the prepared script produces 12 users (one admin,
three managers, eight employees) and 24 tasks across three teams.

The backend database account is restricted to `SELECT`, `INSERT`, `UPDATE`, and
`DELETE` on `taskdb.*`; schema setup uses a separate administrative account.

## Backend runtime environment

Export these variables into the shell that starts the backend, or configure them
in your hosting service. `backend/.env.example` is a reference template; Spring
Boot does not automatically load `.env` files.

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP server port | `8080` |
| `DB_HOST` | MySQL service hostname, without scheme or port | Required |
| `DB_PORT` | MySQL service port | Required |
| `DB_NAME` | Application database | `taskdb` |
| `DB_USERNAME` | Restricted application database user | `etm_app` |
| `DB_PASSWORD` | Password for that database user | Required |
| `DB_TRUSTSTORE_URL` | File URI of the PKCS12 truststore containing the service CA | Required |
| `DB_TRUSTSTORE_PASSWORD` | Truststore password | Required |
| `JWT_SECRET` | Base64-encoded signing key containing at least 32 random bytes | Required |
| `JWT_ISSUER` | Expected token issuer | `employee-task-manager` |
| `JWT_EXPIRATION_SECONDS` | Access token lifetime | `1800` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins | `http://localhost:4200` |

Download the CA certificate from your database provider. Create a dedicated
truststore once using the JDK's `keytool` (replace the example paths):

```sh
keytool -importcert -alias aiven-ca -file /path/to/aiven-ca.pem -keystore /path/to/aiven-truststore.p12 -storetype PKCS12
```

The command prompts for a new truststore password and certificate confirmation.
Set `DB_TRUSTSTORE_PASSWORD` to that password. `DB_PASSWORD` remains the password
for the database account. Use an absolute file URI for `DB_TRUSTSTORE_URL`, such
as `file:///D:/certificates/aiven-truststore.p12` on Windows or
`file:///etc/secrets/aiven-truststore.p12` on Linux. Mount the truststore as a
deployment secret file when deploying; it is excluded from Git.

The Connector/J configuration uses `VERIFY_IDENTITY` to check both certificate
trust and the database hostname. It uses the explicit PKCS12 store and disables
fallback to the system truststore. Hibernate validates the existing schema with
`ddl-auto=validate`; SQL initialization is disabled. Database-generated timestamps
are mapped back to the entities, and entity relationships use lazy loading.

After setting the variables, start the backend from its directory:

```powershell
.\mvnw.cmd spring-boot:run
```

Successful startup initializes the connection pool and JPA EntityManagerFactory,
then logs `Started EtmApplication`. Set JWT_SECRET before startup. Persist the same
signing secret in deployment settings; changing it invalidates existing tokens.
The backend uses stateless Bearer authentication rather than a development form login.

## Implemented API endpoints

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/auth/login` | Public; JSON email/password; returns token, role, name |
| POST | `/api/auth/register` | ADMIN; same creation service as POST /api/users |
| GET | `/api/users` | ADMIN; paginated search/filter results |
| GET | `/api/users/{id}` | ADMIN only; returns a user DTO without password/hash |
| POST | `/api/users` | ADMIN; create an active user, returns 201 |
| PUT | `/api/users/{id}` | ADMIN; edit details/role/team/active state, returns 200 |
| DELETE | `/api/users/{id}` | ADMIN; soft deactivate, returns 204 |

Send protected requests with `Authorization: Bearer <token>`. Tokens use HS256 and
validate signature, issuer and expiration. Current database role and active state
are checked for every authenticated request. The admin-only service also uses
`@PreAuthorize`. Other required endpoints will be added with the remaining features.

Errors contain `timestamp`, `status`, `message`, and `fieldErrors`. Invalid credentials
or tokens return 401; insufficient permissions return 403; input errors return 400.
Frontend logout will remove the token; server-side token revocation and refresh
tokens are not implemented.

## User management

`GET /api/users` accepts zero-based `page` (default 0), `size` (default 10, maximum
100), `search` (name/email substring, case-insensitive), `role`, and `active`.
Without `active`, both active and inactive users are included. Results are ordered
by ID and return `content`, `page`, `size`, `totalElements`, and `totalPages`.
Search treats wildcard characters as literal text.

Example creation body:

```json
{
  "fullName": "New Employee",
  "email": "new.employee@demo.com",
  "password": "Employee@123",
  "role": "EMPLOYEE",
  "managerId": 2
}
```

For ADMIN or MANAGER, `managerId` must be null or omitted. New users are active.
For an edit, send `fullName`, `email`, `role`, `managerId`, and `active`.
`password` is optional: omitted/null/blank preserves the previous hash; a supplied
password is BCrypt-hashed. Passwords must fit BCrypt's 72-byte UTF-8 limit.
Emails are normalized and remain unique even for inactive accounts.

`DELETE` deactivates the account and preserves its record and tasks. An inactive
account can be restored through an edit with `active=true` and a valid manager
when applicable. Repeated deactivation returns 204. Managers must reassign their
active team before deactivation or a role change. The last active ADMIN cannot be
deactivated or demoted. These state conflicts return 409.

Administrative writes lock the active-admin rows inside a transaction, recheck
the caller's administrative state, and lock affected users/managers. Unique email
constraints also protect concurrent creates. Lock contention conflicts return 409
with a retry message. Actual concurrent MySQL behavior has not yet been verified
by the mocked tests.

Import `docs/postman/employee-task-manager.postman_collection.json` for manual
authentication and CRUD checks. Set fresh role tokens in its collection variables;
new-user requests store returned IDs for later edits. Use a unique test email or
restore the existing account when repeating tests. Saved token values should not
be committed in exported collections.

## Demo application accounts

These credentials are for the seeded application accounts. They are separate from
the database connection credentials. Live login has been verified.

| Role | Email | Password |
| --- | --- | --- |
| ADMIN | `admin@demo.com` | `Admin@123` |
| MANAGER | `manager@demo.com`, `manager2@demo.com`, `manager3@demo.com` | `Manager@123` |
| EMPLOYEE | `emp1@demo.com` through `emp8@demo.com` | `Emp@123` |

The first manager has three employees, the second has two, and the third has three.
The first manager assigned 11 tasks; the second and third assigned six each.
One additional task was assigned by the admin. This supports pagination and checks
that managers only see tasks they assigned.

## Business rules and assumptions

- Each employee belongs to one active manager. Admin and manager accounts do not
  themselves have a manager. The database checks whether a manager reference is
  present for the appropriate role; the service will validate the referenced
  user's role and active state.
- An admin may transfer an employee after their unfinished tasks have been
  completed or reassigned within the original team. Completed task history is
  preserved. This transfer policy is an implementation choice.
- User deletion deactivates the account to preserve task history.
- Role changes require all related unfinished tasks (assigned to or by the user)
  to be resolved first. Completed history alone does not block a role change.
- User/team/administrator rules are enforced in the service layer. Task ownership
  and status-transition rules are implemented in TaskService.
- Self-service password change is deferred until the required core features are
  complete. Current password edits are administrative resets.

## Task API

| Method | Path | Permission |
| --- | --- | --- |
| POST | `/api/tasks` | ADMIN or MANAGER; active EMPLOYEE assignee |
| GET | `/api/tasks` | ADMIN all; MANAGER original assigner; EMPLOYEE assignee |
| GET | `/api/tasks/{id}` | Same ownership scope |
| PUT | `/api/tasks/{id}` | ADMIN or original MANAGER |
| PUT | `/api/tasks/{id}/status` | Assigned EMPLOYEE only |
| DELETE | `/api/tasks/{id}` | ADMIN only; physically deletes the task |

Create/edit body:

```json
{
  "title": "Prepare monthly summary",
  "description": "Summarize completed work",
  "priority": "HIGH",
  "deadline": "2026-11-04",
  "assignedTo": 3
}
```

Title is required (maximum 200 characters); priority and assignedTo are required.
Description is optional (maximum 10,000 characters); deadline is optional. Past
calendar dates are allowed so existing overdue tasks can still be edited.
The backend sets new status to PENDING and assignedBy to the authenticated user.
Edit preserves status and original assignedBy. Managers assign only within their
own team. ADMIN may assign new tasks across teams. Only PENDING tasks can be
reassigned; a task originally assigned by a manager stays within that original
manager's team when reassigned, including by ADMIN. These reassignment constraints
are agreed design choices, not additional explicit assignment requirements.

Status body is `{"status":"IN_PROGRESS"}` or `{"status":"DONE"}`. Only the assigned
employee can advance PENDING → IN_PROGRESS → DONE. Skipping/reversing returns 409;
repeating the current status is an idempotent 200. ADMIN/MANAGER cannot use this
endpoint. Completed task metadata remains editable by its authorized assigner or
ADMIN, but its assignee/status history is preserved.

List parameters: `page` (default 0), `size` (default 10, maximum 100), `status`,
`priority`, `assignee` (positive user id), and `role` (assignee's current role).
Results use the same PageResponse shape as users and sort by descending task id.
Role refers to the assignee's current role, which may change after completed tasks.
Every filter narrows the server-side ownership scope; it never widens access.
Existing out-of-scope task ids return 403; unknown ids return 404. Responses contain
participant ids/names but no email, password or user entity. Seed counts are a
baseline; manual CRUD requests may change the database totals.

Task writes share UserService's active-admin row lock before reading current
actor/team state. This serializes writes with user transfers/deactivation and
prevents stale state from authorizing an assignment. It favors consistency over
write throughput for this small demo. Mocked tests do not establish real MySQL
concurrency behavior; live database verification is still required.

Use the Postman **Task management** folder in order. Set fresh role-token variables;
its description identifies the seeded accounts. It stores createdTaskId and deletes
only that newly created task. No schema re-import is needed.

## Dashboard API

`GET /api/dashboard/stats` requires authentication and uses the current database
role of the authenticated account. It does not accept a user id or role override.

| Role | Counts |
| --- | --- |
| ADMIN | totalUsers (all accounts), activeUsers, all task counts |
| MANAGER | teamSize (own active EMPLOYEEs), own-assigned task counts |
| EMPLOYEE | own assigned task counts |

All responses contain `role`, `totalTasks`, `completedTasks`, and `tasksByStatus`
with numeric `pending`, `inProgress`, and `done` fields. Missing status groups are
zero. Global user fields are omitted for MANAGER/EMPLOYEE; teamSize is omitted for
ADMIN/EMPLOYEE. Historical tasks involving inactive employees still count;
manager teamSize counts only active employees. Database COUNT/GROUP BY projections
avoid loading all task entities. Each request reads counts inside a read-only
transaction. Compare scoped totalTasks with the task-list totalElements for the
same account; manual CRUD may change the initial seed totals.

Use the Postman **Dashboard** folder for all three roles and missing-token checks.
No schema import or migration is required for this feature.

## PDF report API

`GET /api/reports/tasks/pdf` returns a PDF attachment named `tasks-report.pdf`.
ADMIN receives all tasks. MANAGER receives tasks belonging to the current team's
EMPLOYEEs, regardless of who assigned them, including ADMIN-assigned tasks.
Employees with a current manager relationship remain in that team's report even
when inactive; tasks of users whose current role is no longer EMPLOYEE are not
part of a manager's team report. ADMIN still sees all task history.
EMPLOYEE requests return 403; missing/invalid authentication returns 401.
The backend derives report scope from the current authenticated account; request
parameters cannot override the manager or role. Responses use application/pdf,
Content-Disposition attachment, and Cache-Control no-store. Errors remain JSON.

The team report scope deliberately differs from the manager task list/dashboard,
which show only tasks the manager originally assigned. Report access does not
grant edit permission. After an employee transfer, their tasks appear in the new
manager's team report. Previous managers retain their original assignment history
through the task list. This is the agreed interpretation of 'own team only'; the
specification does not define historical team membership reporting.

The landscape A4 report contains scope, generated-by name, UTC timestamp, task
count and columns ID, Title, Assignee, Assigned by, Status, Priority and Deadline.
Long cells wrap and continue across pages, column headers repeat, and pages have
numbered footers. Empty reports explicitly state that no tasks were found.
Apache PDFBox 3.0.8 is pinned in Maven. The current English report uses standard
PDF fonts: unsupported glyphs, including Bengali/emoji, are replaced with '?'.
It does not currently implement multilingual font embedding/shaping.

Repository queries project only report columns, avoiding lazy entity traversal
and additional per-row queries. Report rows and the PDF are held in memory, which
is suitable for this demo dataset; large production exports would need bounded
batching and an asynchronous/streaming export design.

Import the Postman **PDF reports** folder. Save successful responses as PDF and
open them to verify scope, columns, wrapping and page numbers. In the unchanged
seed, manager 1's report includes the ADMIN-assigned task for employee 4, while
that task is excluded from manager 1's task list. Manual changes may alter counts.
The user-run renderer tests create `backend/target/report-preview.pdf` for visual
review; target and personal notes are ignored by Git. No schema import is needed.
