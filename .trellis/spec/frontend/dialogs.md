# Shared Dialog Contract

## Scope

All in-app confirmations and text prompts use `components/Dialog` and `DialogProvider`/`useDialog`. Feature-specific forms, such as task completion, reuse the same modal primitive and style. Do not use window.prompt/confirm/alert for application actions. Browser-owned beforeunload remains the platform exception.

## Interaction

- Native HTML dialog `showModal()` supplies modal focus and background inertness; associate a visible title and description.
- `DialogOptions.className` may apply a feature theme (community uses `community-dialog`) without replacing the native primitive or changing default workbench behavior. An asynchronously loaded profile form autofocuses its first real input when mounted, while closing restores its original trigger.
- Restore the trigger focus after closing when the element still exists.
- Cancel and Escape resolve without mutation or navigation. Ignore cancellation during an in-flight submit.
- Await the user's decision before changing a date, report version, page, or page size. Avoid duplicate parent/child confirmations.
- Destructive actions have a red confirm button and explain impact; cancellation is the initial focus for destructive confirmation.
- Prompt inputs retain their value on validation/network failure. Task results permit empty text, multiple lines and at most 4000 characters; Enter inserts a newline and Ctrl+Enter submits.
- Guard duplicate submits synchronously as well as with disabled buttons. A second confirmation cannot replace an in-flight operation.
- Close once the write succeeds. Show subsequent list-refresh failures separately from the successful write, with a refresh action.

## Verification

Browser tests verify cancellation without writes, successful confirm exactly once, error draft retention, task completion with an empty result, result editing, dirty navigation cancellation, keyboard use, and desktop/narrow-screen layouts. Tests interact with DOM dialogs rather than accepting native page dialog events, except for actual beforeunload.
