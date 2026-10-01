# Technical design

## Boundaries

Keep the account-root controller and existing checkpoint fields. Server time, session/version, controller ID/generation/expiry and settlement remain authoritative. Client owns successful sound opt-in, current audio status and local pending/dismissed alarm state.

## Lease lifecycle

Only successfully enabled tabs submit controller claims. Hidden owners continue sending ID and generation; other enabled tabs can acquire an expired lease under the server lock; viewers send no ID.

Use a 120-second server lease, with one Duration constant: Chrome's intensive hidden-tab timer checks can be one minute apart after five minutes of silence, so a 60-second TTL provides no network/dispatch margin. A 120-second lease tolerates that ordinary background cadence while retaining bounded expiry/takeover. Test 61-second renewal cadence and the exact expiry boundary; clients derive scheduling from returned expiry instead of hardcoding TTL. A disappeared controller can take up to 120 seconds to expire.

Target-ended checkpoints with a claim may mutate the reminder lease only, preserving version/owner checks and generation fencing. They must not advance focus or settle again. Early-ended sessions gain no completion reminder behavior. The target-reaching checkpoint returns a usable lease after settlement.

Live-session refresh must resolve the known session when `/current` becomes null, rather than silently losing a completion settled in another tab. Conflict recovery refetches and retries lease acquisition only while the reminder is pending. Initial reload must not discover/replay historical ended sessions.

Pending alarms renew until dismissal. Recheck session ID, pending status, account lifetime and authoritative lease after every await. Stop on lease loss. Bound looping source lifetime by the lease expiry using audio-clock scheduling as well as client checks, avoiding overlapping sound when JavaScript callbacks are delayed and another tab takes over.

## Audio lifecycle

Separate opt-in from ready/paused/closed/error state. Automatic playback resumes only an existing successfully activated context. A user gesture may create/replace a closed context, resume and preview immediately, then resolve ownership for a pending alarm.

Generate a pulse-and-silence buffer and loop it with AudioBufferSourceNode. Stop/disconnect resources on dismissal, expiry and account cleanup. Guard pending resumes against overlap, session changes and late replay. Observe audio state changes and display paused/closed feedback without discarding opt-in. Apply recoverable-context behavior to existing microbreak audio too.

## Completion and UI

Remove foreground-only gates from target detection and immediate sync while preserving foreground microbreak prompting. Keep low-frequency checkpoints and immediate return calibration. Server-anchored target scheduling may reduce detection latency; more frequent polling is not the solution to background throttling.

Never-enabled sound offers enable/preview; unavailable enabled audio offers restore; other/expired controller reports ownership separately. Do not label internal leases as browser permission or show ready text for paused audio.

## Compatibility and rollback

No schema migration, compatibility adapter, capability gate or legacy fallback. Existing API fields suffice. Rollback is a scoped code revert without data conversion.

## References and risks

- Audio states/resume: https://developer.mozilla.org/en-US/docs/Web/API/BaseAudioContext/state
- Buffer looping: https://developer.mozilla.org/en-US/docs/Web/API/AudioBufferSourceNode/loop
- Timer throttling: https://developer.chrome.com/blog/timer-throttling-in-chrome-88

Record observed detection delay instead of promising an exact deadline. Device sleep/freeze/eviction are out of scope. Primary implementation risk is the combined terminal-version/lease-takeover/async-audio race.
