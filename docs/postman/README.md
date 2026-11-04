# Postman API collection

Import [employee-task-manager.postman_collection.json](employee-task-manager.postman_collection.json).
The collection contains 55 requests with response assertions. Exported tokens and
generated record IDs are empty; only published demo application passwords are included.

## Connection and execution

1. Select **No Environment**, or remove environment variables that shadow the
   collection's `baseUrl`/token/ID variables.
2. Set collection variable `baseUrl` to `http://localhost:8080` or
   `https://employee-task-manager-api-07j1.onrender.com` with no trailing `/api`.
3. For localhost use Postman's desktop app or Desktop Agent. Keep Aiven Running
   and allow a free-hosted backend to finish starting before the workflow.
4. Run the complete collection with all 55 requests selected, one iteration and
   no data file. Keep numbered folders in order: **01 - Login setup (run first)**
   must be first and contains all five demo-role logins. Successful
   responses automatically capture the five role-token variables; there is no
   need to copy tokens manually. Failed logins clear the corresponding stale token.
5. No separate manual login is needed for a full run. If authentication/creation
   fails, resolve that failure before dependent requests. For a full Collection Runner execution,
   review each result; do not treat earlier token/data failures as independent bugs.

If an older copy is already imported into Postman, re-import the updated JSON;
editing the file in this repository does not update Postman's stored copy. In the
Runner, confirm that the first request is **Admin login - expect 200** under
**01 - Login setup (run first)**, followed by the other four role logins. A fresh
import defaults to localhost, so set `baseUrl` again when testing the live backend.

## Coverage

| Area | Checks |
| --- | --- |
| Authentication | Login contracts, wrong credentials, validation, missing/invalid token |
| ADMIN users | Pagination/search, create/register, edit with preserved password, soft delete, inactive record, forbidden creation |
| Tasks | Scoped lists, combined assignee-role/status/priority/user filter, own-team assignment, cross-user denial, status progression, reassign conflict, ADMIN delete |
| Dashboard | Scoped counts, status totals, omission of unauthorized global fields |
| Reports | ADMIN/current-team PDFs and headers, EMPLOYEE/missing-token denial |
| Team options | Role-scoped minimal employee-option DTOs |
| Health | Public GET/HEAD, minimal response, no-store, empty HEAD body |

The workflow assumes seeded manager1 ID2 and employee1 ID3 belong together;
manager2 ID5 / employee3 ID6 provide the other-team checks. If the demonstration
data was edited, use appropriate current accounts/IDs rather than re-importing
the schema over live tables. Manual CRUD changes counts; no tests require exact
global seed totals. The assignee-role parameter means the assignee's current role.

## Data and token handling

The user folder generates a fresh `testRunId` when creating its test employee,
uses that ID for unique employee/admin emails, and captures their returned IDs.
It soft-deletes only those new accounts, preserving database history. Repeated
runs therefore increase total user records even after cleanup. The task folder
creates, progresses and deletes its own task; seeded tasks are not edited.
Creation hooks clear the relevant old IDs before sending a new create request.

Do not run individual destructive requests with manually substituted seed IDs.
Use Send and Download / Save Response for PDFs and visually check their scope and
layout. Expected HTTP200 health requests assume an available database; backend
automated tests simulate connection failures without shutting down the live DB.

Tokens expire (default 30 minutes); rerun logins when needed. Before exporting or
committing a collection, clear all token/created-ID/testRunId values, including
any current values saved by Postman. Do not commit personal reports or credentials.

## Verification boundary

The saved JSON, variable references, empty sensitive variables, mandatory API
coverage and presence of assertions were inspected. The revised collection has
not been executed against a live server by the assistant. Run it manually and
review results separately from backend unit/MVC test results.
