# Fix linked task completion on focus progress save

## Goal

Saving ended focus progress completes the linked task with the same result and returns to the new focus form.

## Requirements

- Saving ended linked focus progress completes its task with the same trimmed completion result.
- Progress and completion succeed in one transaction. Failure retains entered text for retry.
- Linked saves require nonblank progress; unlinked saves keep optional progress and create no task.
- Timer end alone does not complete tasks. Successful progress save refreshes shared views and returns to a new focus form.
- Preserve a different queued task. Prevent duplicate completion records; synchronize results if the task was completed elsewhere.
- Retain owner/version rules. No compatibility gates, migrations or fallback paths.

## Acceptance Criteria

- [ ] Linked task remains pending on timer end, then completes with matching completionResult on save.
- [ ] One active completion record and appropriate task events are retained on retries.
- [ ] Blank or failed linked saves keep the form and text and allow retry.
- [ ] Successful save clears prior progress/linkage, preserves a queued next task, and shows the new focus form.
- [ ] Unlinked save creates no todo and resets the form.
- [ ] Backend regression tests, browser regressions, frontend lint/build pass or environment limitations are documented.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
