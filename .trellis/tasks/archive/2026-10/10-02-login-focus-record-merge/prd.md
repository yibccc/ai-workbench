# Login password visibility and linked focus record merge

## Goal
Replace the login password text toggle with eye icons, and present linked task completion and focus time as one daily record.

## Confirmed Facts
- Login already toggles password visibility with a text button: frontend/src/features/auth/LoginPage.tsx:24.
- Focus settlement writes daily FOCUS_SESSION records; saving linked progress completes the task and creates TASK_COMPLETION: backend/src/main/java/com/aiworkbench/service/impl/FocusServiceImpl.java:238-255.
- RecordsPage consumes a server-paginated list: frontend/src/features/records/RecordsPage.tsx:34.
- Raw focus and task-completion facts have distinct uses in timing statistics and report evidence: .trellis/spec/backend/focus-routines.md.

## Requirements
- R1: Show eye / crossed-eye icons for password visibility. Keep the password hidden initially, accessible action labels and keyboard activation, and normal login submission.
- R2: On a selected daily records page, merge active task completion and all same-day focus records for the same linked task into a single item showing task content, one completion result, summed focus and break durations.
- R3: Apply merging before pagination so item count and page count reflect visible entries. Unlinked focus, focus without a same-day active completion, standalone task completion and manual records remain independent.
- R4: Retain raw owner-scoped timing and completion facts for statistics, task reopen/result edits and report evidence. No schema migration, compatibility layer or feature gate.

## Acceptance Criteria
- AC1/R1: Eye button changes the input type in both directions, keeps its value, offers the correct accessible label, and does not submit the login form.
- AC2/R2: Finishing linked focus and saving its result displays one daily entry with completion result and timing; repeated saves do not duplicate it.
- AC3/R2: Multiple same-day sessions on the task contribute their durations once each; another day's sessions are not folded into today's completion.
- AC4/R3: Pagination totals match merged entries, including pairs that would otherwise cross a page boundary. Unrelated or another owner's records never merge.
- AC5/R3-R4: Reopening the task reveals retained focus entries; editing the active result updates the merged display. Raw focus statistics and report evidence remain intact.

## Out of Scope
Report rendering redesign, deletion of historical ledger facts, cross-day merging, login/account redesign and unrelated untracked docs/dev-sop files.

## Decision Status
User approved the final summary and implementation on 2026-10-02: merge the daily presentation while retaining source facts.
