# Employee & Task Manager — Angular frontend

Angular 13.3.12, Angular CLI 13.3.11, TypeScript 4.6.4 and Bootstrap 5.3.8.
Use Node 16.20.2/npm 8.19.4 (portable project tools or .nvmrc).

## Local development

From this directory, run `npm install`, then `npm run build` and `npm run test:ci`.
The test command requires a Chrome installation for ChromeHeadless.
After the lockfile is generated, use `npm ci` for reproducible clean installs.
Run `npm start` and open http://localhost:4200. Keep the Spring backend running
at http://localhost:8080. Scripts use the project's local Angular CLI.

Current pages: login, role-based dashboard, ADMIN Users CRUD, role-scoped Tasks
CRUD/status controls and ADMIN/MANAGER PDF download/preview. The user completed
a clean Node 16 install. The current production build succeeds at 499.89 kB and
all 18 frontend tests pass. Management-page live browser verification is pending.

## Configuration and architecture

`src/environments/environment.ts` sets the development public API URL.
`environment.prod.ts` currently uses /api; replace it with the deployed backend
URL before production deployment. Never put DB credentials or JWT signing secrets
in Angular environment files. Browser bundles are public.

core/auth contains session lifecycle, API bearer-token interceptor and route guard.
core/api contains models, backend services and common error messages. Feature pages
use Reactive Forms and HttpClient; shared components supply icons and toasts.
Global CSS builds the approved light/navy/teal responsive theme over Bootstrap.
The role label is a display badge, not a role-switching control.

The access token is held in sessionStorage and cleared at expiry/logout/401.
Client JWT decoding is for expiry/UI only; backend validation is authoritative.
403 does not log the user out. Client route guards complement backend authorization.
Only configured backend API requests receive the bearer token.
Manager dashboard/tasks are own-assigned; PDF reports cover the current team,
including administrator-assigned tasks.

## Legacy build compatibility

Use the project .npmrc engine checks and Node16/npm8. A scoped override
selects node-releases2.0.44 for Browserslist's JSON release metadata, because newer
releases require Node18. This intentionally substitutes an older metadata snapshot
for the declared newer range; browser targets remain browser-based and newer Node
release metadata may be absent. Verify lockfile updates with build/tests.

src/bootstrap.scss includes used Bootstrap components/utilities instead of the
entire precompiled stylesheet. Production JS/CSS minification stays enabled;
critical-CSS inlining is disabled because the Angular13 Critters parser cannot
reliably parse some Bootstrap5.3 selectors. The full external application CSS is
still emitted. Initial bundle budget remains500KB warning/1MB error.

The recorded audit has59 findings, including Angular runtime advisories and
critical build/install dependencies. They are not resolved by reinstalling.
Some proposed fixes require Angular major upgrades incompatible with the specified
Angular13 stack. Do not claim this legacy stack has zero vulnerabilities or force
major dependency overrides without compatibility review. Deployment remains
pending; document these limitations for evaluation and revisit the stack before
production use.

## Management/UI milestone (verification pending)

Users support name/email search, role filters, pagination, create/edit, manager
selection, optional password reset and Delete confirmation. The UI lists only
active users. DELETE soft-deletes the backend record and revokes login while
preserving task history; inactive/restore management controls are not exposed.
Tasks support status/priority/current-active-assignee filters, pagination, details,
assignment/editing, employee start/done transitions and ADMIN delete confirmation.
Assignee/manager option lists are searchable and paginated, not capped to the
first result page. The minimal /api/team/employees endpoint scopes options on the
backend. Assignee filters currently select active current-team employees; historical
or inactive assignees' tasks remain visible in unfiltered lists and through API
assignee filtering. Only pending tasks expose an editable assignee.

Shared dialogs trap keyboard focus, close on Escape when idle, lock background
scroll and restore focus. Toasts appear at top-right (top on mobile). The account
avatar opens name/role/logout options. The dashboard displays local date/time,
updated on minute boundaries. Preview PDF is optional alongside required Download
PDF; it opens a tab directly from the user's click and loads the authorized blob.
Popup blocking is handled with a message and download remains available. Object
URLs are retained while previews are open and released when their tabs close.

User-run management milestone: production build499.89KB, no engine/CSS/budget
warnings and18 frontend tests passed. Backend team-lookup tests also pass within
the152-test suite. Live management/browser checks and deployment remain pending.

## Dropdown/filter correction (verification pending)

Person selection now uses a single searchable popover with direct option buttons,
selection/outside/Escape close, empty state and a10-second retry timeout. It no
longer nests a native select inside a filter popover. Users/Tasks each have one
Clear filters action that resets the complete filter set and pagination. Searchable
options are a usability choice; mandatory Users name/email search remains.
User-run verification: all 25 frontend tests pass, production build456.32KB
with no budget/CSS/engine warnings. Live dropdown/filter behavior checks remain
pending. The subsequent Delete/list/details refinements have not been applied yet.

## User list and task detail refinement (verification pending)

The Users table now has compact identity rows, explicit Actions heading and
Edit/Delete buttons. It always requests active=true so deleted accounts disappear
from the list. Backend soft deletion, historical records and administrator/team
safeguards remain unchanged. The UI no longer exposes account-state/restore
controls. Tasks have a visible View details action that opens the existing
read-only description/assignee/assigner/status/priority/deadline dialog.
Three regression tests cover active-list scope, delete reload and safeguard errors.
The current refinement passed all28 frontend tests and a456.46KB production
build with no build warnings. The user confirmed the requested live UI checks.
