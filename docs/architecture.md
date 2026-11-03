# Backend architecture

The backend uses **feature packages with layer subpackages**. Each business feature
owns its HTTP entry points, application rules and data contracts. It only has
layers it actually needs; there are no empty controller/service/repository folders.

```text
io.github.abdulmmh.etm/
├── EtmApplication.java
├── auth/
│   ├── controller/      AuthController
│   ├── dto/             LoginRequest, LoginResponse
│   └── service/         AuthService, DatabaseUserDetailsService
├── user/
│   ├── controller/      UserController
│   ├── dto/             CreateUserRequest, UpdateUserRequest, UserResponse
│   ├── entity/          User
│   ├── enums/           Role
│   ├── repository/      UserRepository, UserSpecifications
│   └── service/         UserService
├── task/
│   ├── controller/      TaskController
│   ├── dto/             TaskRequest, TaskStatusRequest, TaskResponse
│   ├── entity/          Task
│   ├── enums/           TaskStatus, TaskPriority
│   ├── repository/      TaskRepository, TaskSpecifications, TaskStatusCount
│   └── service/         TaskService
├── team/
│   ├── controller/      TeamController
│   ├── dto/             EmployeeOption
│   └── service/         TeamService
├── dashboard/
│   ├── controller/      DashboardController
│   ├── dto/             DashboardResponse
│   └── service/         DashboardService
├── report/
│   ├── controller/      TaskReportController
│   ├── dto/             TaskReportRow
│   ├── renderer/        TaskPdfRenderer
│   └── service/         TaskReportService
├── health/
│   ├── controller/      HealthController
│   ├── dto/             HealthResponse
│   └── service/         HealthService
├── security/            JWT, authentication conversion, CORS and security configuration
└── shared/
    ├── dto/             PageResponse
    └── error/           Shared API errors and exception handling
```

## Responsibilities

| Package | Responsibility |
| --- | --- |
| `controller` | HTTP mappings, request validation and response status; delegates business operations |
| `dto` | Request/response records and report data; separate from persistence entities |
| `service` | Use cases, transactions, role/ownership/team/status rules |
| `repository` | Spring Data access, query specifications and database aggregate projections |
| `entity` | JPA mappings, persistence relationships and database-generated fields |
| `enums` | Domain role/status/priority values |
| `renderer` | PDF document layout and rendering |
| `security` | Cross-feature authentication, JWT verification and HTTP access configuration |
| `shared` | Reusable contracts and errors used by multiple features |

`TaskStatusCount` is a repository query projection, not an API request/response,
so it lives beside the repository. `TaskReportRow` is the report's data transfer
record; JPQL constructor queries reference its fully qualified `report.dto` name.
Features such as dashboard/team/report reuse user/task repositories rather than
creating duplicate repositories for the same database entities.

## Request flow

```text
Angular HttpClient + Bearer token
  -> Spring Security JWT validation and current database account check
  -> feature.controller (validated request DTO)
  -> feature.service (authorization, ownership and business rules)
  -> feature.repository -> JPA entity -> MySQL
  -> response DTO -> JSON -> Angular
```

Public login verifies the password and issues a JWT. The exact public health
GET/HEAD path checks database connectivity without returning internal details.
Error responses are handled by security handlers or `shared.error`.

`EtmApplication` remains in the common parent package, so Spring component,
repository and entity discovery include the feature subpackages. Entity/table
names, URL paths, DTO JSON fields, role rules and schema are unchanged by package
organization. Service unit tests mirror their corresponding layer; the cross-feature
HTTP/security suite remains under the `auth` test package.

See [UML and ER diagrams](diagrams/README.md) for domain relationships and scope.
