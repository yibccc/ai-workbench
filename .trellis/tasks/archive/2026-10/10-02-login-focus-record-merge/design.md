# Design

## Boundaries and Data Flow
Implement daily record merging in the backend paginated record query, before PageHelper applies counting and limits. Use active task-completion records as the presentation anchor. Fold only owner/task-matched active focus rows within the same requested business-day window into that anchor. Sum focusMs and breakMs; keep the current completion result as the single result. Exclude folded focus rows from the presentation query. The frontend displays timing on the merged task entry without inventing a new persisted source.

Keep raw findBetween/findById behavior for focus summaries, report evidence and source detail. Source records remain immutable with respect to this presentation merge; task result editing and reopening continue to use the task service. No database migration or old-data compatibility path is introduced.

Reuse the existing SVG Icon component for eye and eye-off; retain the password input and type=button toggle with accessible labels.

## Risks and Tradeoffs
Owner, day and task boundaries must appear in both aggregation and exclusion predicates. Same-day grouping intentionally combines all sessions for a completed task, even when the user used a different session title. A task completed on another day does not absorb today's focus. Reopened/inactive completions cannot hide focus. PageHelper count SQL must work with the chosen projection.

## Rollback
Revert query/projection and frontend changes together; no persistent data is rewritten.
