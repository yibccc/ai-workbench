# Implementation boundary and decisions

Smallest behavior gap: the existing authenticated account tree has five retained private workspaces but no member publication space, publisher editor or protected binary decoder. New community/publishing features own their business state; App/AppShell only add strict routes, stable shared chrome and an editor leave guard. Shared HTTP retains Cookie/CSRF/FormData/identity epochs while selecting JSON or Blob decoding and checking identity after decoding.

Necessary files: `frontend/src/features/{community,publishing}/`, `api/{community,publishing,http}.ts`, `components/layout/{routes,AppShell}.tsx/ts`, App, LoginPage/AccountMenu, optional shared Dialog theme, pinned Markdown package/lock, community E2E and a frontend-only Vite test launcher. Backend/Compose/root scripts/user SOP documents remain owned by other agents. No compatibility aliases, feature flags, old-schema path, cloud operations or git commit/push are added.

- The same AppShell main node remains in the same React tree path when switching spaces. Original five RetainedViews and the unique root focus controller/audio remain mounted; only account change unmounts them.
- New draft route reads do not create posts. Explicit save/upload/publish may create a private post; preview of an unsaved local new draft stays local. Once explicitly saved, its UUID route can reload the server draft.
- Editor local input, acknowledged snapshot and server aggregate version are separate. Single-post writes/upload requests serialize. Late save acknowledges only its captured snapshot and never replaces newer input. Removal explicitly saves private reference changes with acknowledged text, preserving newer body/title/summary.
- Publication submits the exact confirmed full input/READY collection/version/requestId in one command. Lost-response retries preserve requestId and entire payload. A receipt is followed by owner reread before granting current status/version; a replay after later changes keeps the local editor and reports the current state.
- Images use current attachment UUID allowlists and identity Blob reads; Markdown skips HTML, permits http/https links, disables external images and adds no GFM/raw HTML. Every image URL is revoked on replacement/unmount. Downloads share identity cancellation and survive React StrictMode effect setup/cleanup.
- Vite E2E launches in an allowlisted OS environment plus its required `VITE_API_TARGET`; backend/storage credentials are never inherited by the frontend child.

Validation is ongoing; see validation.md. Ordinary fixed issues are recorded here for final aggregation rather than interrupting the approved execution.
