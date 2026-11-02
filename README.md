# Employee & Task Management System

A full-stack web application for managing employees and tasks with Admin, Manager,
and Employee roles.

## Current status

The backend starter and MySQL schema are in place. The demo dataset has been
prepared and manually imported: 12 users and 24 tasks across three teams.
Authentication, backend database configuration, business features, and the Angular
frontend are the next implementation steps.

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

Compilation verifies the starter's dependency resolution and Java source. It
does not verify database connectivity or application startup. Database connection
settings will be supplied through environment variables in the database setup
step. Automated tests have not been added yet.

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
