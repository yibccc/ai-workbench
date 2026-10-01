# Design

Change FocusServiceImpl.progress and FocusPage.saveProgress. Keep automatic timing settlement independent from task completion. Inject TaskService and reuse its completion and result-update operations within the existing progress transaction, preserving owner/version checks, events and work-record consistency. Already-completed tasks use the existing result-update contract.

The UI guards progress submission and new-session actions while saving, refreshes shared task/record state, and calls prepareNewFocus only after success. A refresh failure must not misreport the acknowledged write as failed. Read the latest draft through a synchronized ref at reset, so a different task brought in while the save is awaiting its response survives. Show accurate linked completion copy; linked progress is required and unlinked progress remains optional.

Regression coverage checks completion/results, duplicate record prevention, already-completed result synchronization, validation and rollback, successful form reset, failure/retry, queued drafts and unlinked reset. No schema or endpoint additions.
