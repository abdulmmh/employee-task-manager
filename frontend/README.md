# Employee & Task Manager — Angular frontend

Angular 13.3.12, CLI 13.3.11, TypeScript 4.6.4, RxJS 7.5.7 and Bootstrap 5.3.8.
Use Node **16.20.2** / npm **8.19.4**; `.nvmrc`, engines and `.npmrc` pin/check this
toolchain. No global Angular CLI is needed.

Live frontend: https://etm-mahadi.netlify.app
Full setup, credentials and API documentation: [root README](../README.md).

## Commands

Run from `frontend/` with the pinned Node/npm:

```powershell
npm.cmd ci
npm.cmd start
```

Open http://localhost:4200 with the backend running at http://localhost:8080.
Use `npm ci` for a clean lockfile-based install; it is unnecessary before every run.
On Unix use `npm` instead of `npm.cmd`.

```powershell
npm.cmd run build
npm.cmd run test:ci
```

The developer verified a **457.29 kB** production build and **32 passing tests** on
November 4, 2026. ChromeHeadless tests require Chrome. No lint script is configured.

## Configuration

- Development API: `src/environments/environment.ts` → `http://localhost:8080/api`.
- Production: Netlify runs `node scripts/configure-api.cjs && npm run build`.
  Set build variable `API_BASE_URL` to the public HTTPS backend URL ending in `/api`.
  The script validates the URL and writes `environment.prod.ts` before compilation.
- A plain local production build retains the committed `/api` placeholder unless
  the configuration script is run first. For a deployed custom build, set
  `API_BASE_URL`, run the script, then build; inspect generated changes before commit.
- Database passwords and JWT signing keys belong only to the backend. Browser
  bundles, including the API URL, are public. See [deployment](../docs/deployment.md).

## Architecture and behavior

`core/auth` handles session lifecycle, route guards and API bearer-token interception;
`core/api` contains typed models, HTTP calls and error messages. Feature components
use Reactive Forms; shared components provide dialogs, pagination, person pickers,
icons, clock, toast notifications and PDF preview management.

The token is held in sessionStorage and cleared on expiry/logout/current-session
401. Client JWT decoding is for UI/expiry only; backend verification is authoritative.
403 preserves the session. Only configured backend API requests receive a token.

- ADMIN Users: name/email search, role filtering, pagination, create/edit and Delete.
  The UI lists active users; backend DELETE preserves records/history with active=false.
- Tasks: scoped list/details, pagination, status/priority/assignee filters, assignment,
  edit, EMPLOYEE status progression and ADMIN delete. Only PENDING assignees can change.
- ADMIN **Assignee role** filter uses the existing backend `role` parameter (current
  assignee role, not assigner). It combines with other filters and resets with Clear
  filters. Other roles do not see/send it from this UI. Historical completed tasks
  can have assignees whose roles later changed; other role results may otherwise be empty.
- Employee options are active/current-team choices; historical/inactive task owners
  remain queryable through backend filters. Manager/employee pickers are searchable
  and paginated, close on select/outside/Escape and have a 10-second timeout/retry.
- Manager task list/dashboard are own-assigned; PDF reports cover current team
  employees, including tasks assigned by ADMIN. Required Download PDF and optional
  preview are available to ADMIN/MANAGER; popup failures show guidance.
- Responsive shell, validated forms, focus-trapped dialogs, top toasts and local
  minute clock. The avatar menu shows account/logout; it is not a role switcher.

## Legacy compatibility and limitations

A scoped Browserslist override selects `node-releases` 2.0.44 JSON metadata to
avoid newer metadata packages requiring Node 18. This is an intentional older
snapshot outside the newer declared range; verify any dependency/lockfile change.
The project targets browsers, not latest Node versions.

`src/bootstrap.scss` includes used Bootstrap components/utilities. Production JS/CSS
minification is enabled; critical-CSS inlining is disabled because the Angular 13
parser cannot reliably parse some Bootstrap 5.3 selectors. Bundle budget remains
500 kB warning / 1 MB error.

The recorded audit has **59 unresolved findings** (5 low, 30 moderate, 22 high,
2 critical), including Angular runtime and build/install dependencies. Some fixes
need framework/toolchain upgrades beyond the assignment's Angular 13 requirement.
Do not use `npm audit fix --force` without a reviewed compatibility plan.
Login does not yet have a dedicated slow-start timeout/retry flow.
