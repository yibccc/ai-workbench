-- V15 recovery gaps were withheld from accounting. Resume at the first withheld
-- instant so the next checkpoint can account for uninterrupted work. Historical
-- PENDING intervals remain unconfirmed audit rows and are excluded from settlement.
UPDATE focus_sessions
SET anchor_at = COALESCE(pending_start, anchor_at),
    phase = CASE WHEN resume_phase IN ('RUNNING', 'MICRO_BREAK', 'PAUSED')
                 THEN resume_phase ELSE 'RUNNING' END,
    resume_phase = CASE WHEN resume_phase = 'PAUSED'
                        THEN CASE WHEN break_remaining_ms > 0 THEN 'MICRO_BREAK' ELSE 'RUNNING' END
                        ELSE NULL END,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE phase = 'RECOVERY_REQUIRED';

ALTER TABLE focus_sessions DROP CONSTRAINT ck_focus_session_pending;
ALTER TABLE focus_sessions DROP COLUMN pending_start;
ALTER TABLE focus_sessions DROP COLUMN pending_end;
ALTER TABLE focus_sessions DROP CONSTRAINT focus_sessions_phase_check;
ALTER TABLE focus_sessions ADD CONSTRAINT focus_sessions_phase_check
    CHECK (phase IN ('RUNNING','MICRO_BREAK','PAUSED','ENDED'));
