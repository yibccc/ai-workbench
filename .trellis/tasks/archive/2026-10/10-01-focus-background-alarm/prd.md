# Focus background completion alarm

## Goal

An explicitly sound-enabled tab retains reminder control while hidden, detects target completion in the background, and recovers temporarily paused audio.

## Background

Current branch is `feat/focus-completion-alarm`, HEAD `a5e093a`, including continuous alarm commit `b8b1206`; the user's diagnosis references earlier `949026e2`.

- `frontend/src/features/focus/useFocusController.ts:202`: hidden checkpoints omit controller ID.
- `frontend/src/features/focus/useFocusController.ts:71`: non-running audio discards opt-in; lines 97-106 authorize once and pulse every 700 ms; line 207 reenable trusts cached ownership; line 312 restricts completion detection to visible pages.
- `backend/src/main/java/com/aiworkbench/service/impl/FocusServiceImpl.java`: the 60-second lease is only renewed by controller claims; already-ended checkpoints bypass all lease mutation.

## Requirements

- R1: Successfully sound-enabled tabs may claim and renew the versioned controller lease, regardless of visibility. Viewing tabs without sound opt-in cannot claim it. Preserve ID/generation/expiry exclusivity, including settlement and pending completion reminders.
- R2: Separate successful opt-in from playback availability. Resume the existing context before playback when recoverable, then verify running. Classify never-enabled, paused/interrupted, closed and other/expired-controller feedback. First activation and closed-context replacement require a user gesture.
- R3: Reenable resolves pending reminder and authoritative ownership before restarting. Late resume, requests or state changes cannot restart a dismissed reminder or old account audio.
- R4: Detect target completion while hidden and synchronize on return. Use a looping audio buffer instead of JavaScript pulse intervals for continuous sound. Keep microbreak presentation foreground-only.
- R5: Preserve durations, task completion rules, daily records, settlement idempotency, cross-page stop controls, Start audio activation and no historical ended-session replay after reload.
- R6: Add no compatibility layer, old-behavior fallback, rollout flag or default-off gate.

## Acceptance criteria

- AC1 (R1): Hidden sound-enabled controller checkpoints beyond 60 seconds retain its lease; non-enabled viewers cannot take it; competing tabs and stale generations cannot renew another valid controller.
- AC2 (R1, R5): Cover owner/other-tab target settlement, pending already-ended lease renewal, expired takeover and stale-version refetch. Only one authorized tab plays; lease updates do not credit extra time or settle again.
- AC3 (R2, R3): Suspended/interrupted audio reuses the context and resumes when possible. Failed recovery gives accurate actionable feedback. Reenable restarts only a pending authorized alarm; stopping during pending resume/request prevents late playback.
- AC4 (R3, R4): Looping audio stops on dismissal, lease expiry/loss and account unmount. Hidden target detection works without inventing hidden microbreaks.
- AC5 (R4): Exercise >5 minutes hidden, measure completion detection/source start delay, two-tab ownership and return synchronization. Distinguish instrumentation from physical audible-browser evidence.
- AC6 (R5): Early end stays silent; page navigation preserves the stop notice; stop sends no second end request; reload does not replay historical completion; daily settlement remains idempotent.

## Out of scope

Device sleep, frozen pages and system eviction are explicitly excluded by the user. Native alarms/notifications, duration accounting, task completion and daily-record redesign are excluded.

## Decisions

The user approved task creation and continued repair. Scope is the three requested fixes plus necessary lease/settlement/dismissal boundaries. No unresolved product decisions remain. Final planning review precedes implementation under local workflow.
