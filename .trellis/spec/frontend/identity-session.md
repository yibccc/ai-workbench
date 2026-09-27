# Account Identity and Session UI

## 1. Scope / Trigger

Use this contract when changing the login root, account management, shared HTTP client, retained business views, focus controller, pending AI/report tracking, STOMP lifecycle, 5-second Toast, or active-session signaling. The five business workspaces remain the main navigation; account management is an ADMIN-only auxiliary view.

## 2. Signatures

- `api/auth.ts`: `getCurrentAccount`, `login`, `logout`, `signalActivity`, `changeOwnPassword`, `listAccounts`, `createAccount`, `changeAccountRole`, `changeAccountEnabled`, `resetAccountPassword` use the backend `/api/auth/**` and `/api/admin/users/**` contracts. `Account = {id, username, role: ADMIN|USER, enabled, createdAt}`; no password hash or session token is returned.
- `api/http.ts`: `request<T>(path, init?)`, `getCsrfToken()`, `setRequestIdentity(id|null)`, and `setUnauthorizedHandler(handler|null)` own same-origin Cookie/CSRF, 401 classification, aborting old requests, and identity epochs.
- `hooks/realtime.ts`: `trackEntity(kind,id,listener)`, `trackedIds(kind)`, `finishTracking(kind,id)`, and `closeRealtime()` own one identity-scoped STOMP client and the 15-second HTTP fallback. The server endpoint is native `/ws/events`; subscribe exactly `/user/queue/workbench-events` with `X-XSRF-TOKEN` on CONNECT.
- `App.tsx` places `ToastProvider` above the authenticated/login switch. `AppShell` owns desktop sidebar-bottom and mobile topbar account menus; `features/auth/` owns login, password dialog and ADMIN user page.

## 3. Contracts

At startup, read `/api/auth/me` before loading business views. A 401 shows the application login page; a network/server failure shows a retryable connection error, not a false expiry. After login, use the stable account ID as the identity boundary. Ordinary navigation among the five workspaces retains `RetainedView` drafts and filters. Logout, true 401, or A→B account switch aborts in-flight requests, rejects late responses through the request identity epoch, closes STOMP/reconnect/poll timers and focus audio/checkpoint timers, and remounts the business tree under the new identity. Pending INPUT/REPORT IDs use a user-specific storage key; never restore the old global `ai-workbench.pending.v1` key under another account.

Fetch the CSRF token from `GET /api/auth/csrf` and put `X-XSRF-TOKEN` on login and other protected POST/PATCH/PUT/DELETE calls. Send same-origin Cookies automatically. A 401 on an authenticated operation removes interactive business content immediately and shows the existing 5-second Toast on the login page. 403, 404, network errors, form validation, stored AI failures, and unsaved-change dialogs keep their distinct meanings. Toast remains dismissible and each new event restarts its 5000 ms timer.

When navigation and a 401 arrive in the same animation frame, a delayed old-page Toast cleanup must not remove a newer login-targeted Toast. Capture the Toast event ID when scheduling cleanup and clear only that same old event if it still belongs to a different page. A navigation click may trigger `/api/auth/activity` and its 401 before unrelated GETs, so test the actual response order and assert the login Toast persists for five seconds after its own creation.

Only explicit user interaction in the authenticated workbench (business click, input or meaningful key edit) calls the CSRF-protected `/api/auth/activity`, with a bounded 60-second signal interval. Passive initial loads, focus checkpoint/status calls, 15-second fallback GETs, STOMP CONNECT/SUBSCRIBE/heartbeats/reconnects and server push must never signal activity. The backend marker is authoritative; the client does not grant or extend its own session. Account switch must prevent a late activity signal for A from touching B.

The STOMP client receives only owner INPUT/REPORT refresh signals, not business content. Reconnect with CSRF and the current Cookie, subscribe again, then GET tracked objects; duplicate/old events must not cause a write or state regression. If STOMP alone fails, HTTP CRUD and 15-second tracked-object fallback continue. Simple Broker does not provide offline replay. No client business SEND frames are used.

Account UI includes username/role, self-password change and current-session logout; only ADMIN sees an auxiliary user-management page. Forms use the existing Dialog focus/cancel/duplicate-submit rules. Creation defaults to USER and includes username, role, password and confirmation; self-change needs current/new/confirmation; admin reset needs new/confirmation. Username is immutable, there is no account delete, public signup, password recovery, search/bulk account management or notification center. The image reference is a visual guide, not runtime evidence.

When an ADMIN successfully changes their own role, disables their own account, or resets their own password, the success response itself proves the current UI identity is revoked. Close realtime, clear the request identity, and unmount the authenticated tree immediately. Do not wait for an account-list refresh or a later 401; that follow-up request may fail due to network loss.

## 4. Validation & Error Matrix

| Condition | UI/client result |
| --- | --- |
| Anonymous startup / successful login | Application login page / existing five-workspace shell |
| Real Session 401 during business use | Business tree immediately removed; login page with 5-second expiry Toast |
| USER attempts management URL or receives 403 | No management view or mutation; permission feedback without false logout |
| Foreign business UUID 404 / network failure | Resource or network feedback; current identity remains unless a real 401 follows |
| A logs out and B logs in while A requests/events are late | No A draft, pending ID, response or socket appears/writes in B's view |
| Only STOMP disconnects | HTTP operations continue; fallback GET reaches final database state |
| No user action for 604800 seconds despite automatic traffic | Next protected action needs login; automatic traffic never calls `/api/auth/activity` |
| Local form invalid or cancelled | No mutation; input/focus/unsaved context preserved per Dialog contract |

## 5. Good / Base / Bad Cases

- Good: ADMIN opens user management from the sidebar or mobile topbar, creates USER, then A logs out and B logs in; B sees only B's resources and no late A result. Session expiry switches to login immediately while its Toast remains visible for five seconds.
- Base: one user switches between records, tasks, reports, projects and focus without losing drafts or restarting pending tracking; an offline STOMP channel only delays notifications while HTTP fallback still works.
- Bad: key the whole workspace by page to clear data, store a session token in localStorage, treat all 403/404 as expiry, send `/api/auth/activity` from a 15-second poll, or keep the global pending-ID key across accounts.

## 6. Tests Required

- Run `npm run lint`, `npm run build`, and Playwright E2E against explicit isolated PostgreSQL/Redis endpoints with synthetic accounts; never point the E2E reset hook at an existing application volume. The E2E backend profile must use `d9_e2e` only.
- Browser tests must cover desktop/mobile account entry, ADMIN and USER permissions, form fields/cancel/focus, password error and success, five-workspace regression, pagination and dirty-draft guards, 401/403/404 classification, exact 5000 ms Toast, A→B late-response/storage isolation, and user activity signal behavior. Cover focus audio/timer cleanup during A→B switches and prove passive focus synchronization does not call `/api/auth/activity`.
- For self-demotion, self-disable, and self-reset, force the account-list refresh to fail after a successful mutation and assert the workbench still disappears immediately.
- Inject STOMP disconnect/reconnect and assert HTTP CRUD is usable, tracked INPUT/REPORT reaches the final database state through 15-second GET, reconnect re-subscribes, duplicate events do not write, and automatic communication does not call `/api/auth/activity`.
- Compare real screenshots/geometry on desktop, narrow and short viewports using the viewport-layout spec. A generated concept image cannot be acceptance evidence.

## 7. Wrong vs Correct

Wrong: leave the previous account's retained views, global pending ID list and socket alive after a login switch.

```tsx
setAccount(nextAccount) // old RetainedView requests and realtime callbacks can still resolve
```

Correct: change the identity boundary and close old async state before mounting the next user's business tree.

```tsx
closeRealtime()
setRequestIdentity(nextAccount.id) // aborts old requests and advances the response epoch
setAccount(nextAccount)
```
