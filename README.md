# Employee & Task Management System

A full-stack web application for managing employees and tasks with Admin, Manager,
and Employee roles.

## Current status

The backend starter and MySQL schema are in place. The demo dataset has been
prepared and manually imported: 12 users and 24 tasks across three teams.
JPA entities and environment-based database configuration are implemented. Local
backend startup with the configured database connection and schema validation has
been verified. Authentication, business features, and the Angular frontend are the
next implementation steps.

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
database connectivity or application startup. Automated tests have not been added
yet.

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
then logs `Started EtmApplication`. JWT login and application endpoints are not
implemented yet; Spring Security's temporary development login is not the seeded
application login.

## Demo application accounts

These credentials are for the seeded application accounts. They are separate from
the database connection credentials. Login functionality is the next implementation
step.

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
- Role, ownership, status-transition, and transfer rules will be enforced in the
  backend service layer as the relevant features are implemented.
