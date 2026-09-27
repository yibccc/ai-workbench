CREATE TABLE focus_routines (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    title VARCHAR(240) NOT NULL CHECK (btrim(title) <> ''),
    project_id UUID,
    weekdays VARCHAR(16) NOT NULL CHECK (weekdays ~ '^[1-7](,[1-7]){0,6}$'),
    default_duration_minutes INTEGER NOT NULL CHECK (default_duration_minutes BETWEEN 1 AND 480),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_focus_routines_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_focus_routine_project_owner FOREIGN KEY (project_id, user_id) REFERENCES projects(id, user_id)
);
CREATE INDEX idx_focus_routines_owner ON focus_routines(user_id, created_at DESC);

ALTER TABLE todo_items
    ADD COLUMN routine_id UUID,
    ADD COLUMN occurrence_date DATE,
    ADD COLUMN default_focus_duration_minutes INTEGER,
    ADD CONSTRAINT fk_todo_routine_owner FOREIGN KEY (routine_id, user_id) REFERENCES focus_routines(id, user_id),
    ADD CONSTRAINT ck_todo_routine_occurrence CHECK ((routine_id IS NULL AND occurrence_date IS NULL) OR
        (routine_id IS NOT NULL AND occurrence_date IS NOT NULL)),
    ADD CONSTRAINT ck_todo_focus_duration CHECK (default_focus_duration_minutes IS NULL OR
        default_focus_duration_minutes BETWEEN 1 AND 480),
    ADD CONSTRAINT uq_todo_routine_occurrence UNIQUE (user_id, routine_id, occurrence_date);

CREATE TABLE focus_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    task_id UUID,
    project_id UUID,
    title VARCHAR(240) NOT NULL CHECK (btrim(title) <> ''),
    target_ms BIGINT NOT NULL CHECK (target_ms BETWEEN 60000 AND 28800000),
    interval_ms BIGINT NOT NULL CHECK (interval_ms BETWEEN 60000 AND 7200000),
    zone_id VARCHAR(64) NOT NULL,
    phase VARCHAR(24) NOT NULL CHECK (phase IN ('RUNNING','MICRO_BREAK','PAUSED','RECOVERY_REQUIRED','ENDED')),
    version BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    anchor_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    focus_ms BIGINT NOT NULL DEFAULT 0 CHECK (focus_ms >= 0),
    break_ms BIGINT NOT NULL DEFAULT 0 CHECK (break_ms >= 0),
    pause_ms BIGINT NOT NULL DEFAULT 0 CHECK (pause_ms >= 0),
    pending_start TIMESTAMPTZ,
    pending_end TIMESTAMPTZ,
    resume_phase VARCHAR(24),
    break_remaining_ms BIGINT NOT NULL DEFAULT 0 CHECK (break_remaining_ms >= 0),
    next_break_at_ms BIGINT NOT NULL,
    reminders_dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_ordinal INTEGER NOT NULL DEFAULT 0,
    controller_id UUID,
    controller_generation BIGINT NOT NULL DEFAULT 0,
    controller_expires_at TIMESTAMPTZ,
    progress VARCHAR(4000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_focus_sessions_id_user UNIQUE (id,user_id),
    CONSTRAINT uq_focus_sessions_request UNIQUE (user_id,request_id),
    CONSTRAINT fk_focus_session_task_owner FOREIGN KEY (task_id,user_id) REFERENCES todo_items(id,user_id),
    CONSTRAINT fk_focus_session_project_owner FOREIGN KEY (project_id,user_id) REFERENCES projects(id,user_id),
    CONSTRAINT ck_focus_session_pending CHECK ((pending_start IS NULL) = (pending_end IS NULL)),
    CONSTRAINT ck_focus_session_end CHECK ((phase = 'ENDED') = (ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_focus_sessions_one_open ON focus_sessions(user_id) WHERE phase <> 'ENDED';

CREATE TABLE focus_intervals (
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    ordinal BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('FOCUS','BREAK','PAUSE','PENDING')),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    confirmed BOOLEAN NOT NULL,
    PRIMARY KEY (session_id,ordinal),
    CONSTRAINT fk_focus_interval_session_owner FOREIGN KEY (session_id,user_id) REFERENCES focus_sessions(id,user_id),
    CONSTRAINT ck_focus_interval_range CHECK (end_at > start_at)
);

ALTER TABLE work_records DROP CONSTRAINT work_records_source_check;
ALTER TABLE work_records DROP CONSTRAINT work_records_task_source_check;
ALTER TABLE work_records
    ADD COLUMN focus_session_id UUID,
    ADD COLUMN business_date DATE,
    ADD COLUMN focus_ms BIGINT,
    ADD COLUMN break_ms BIGINT,
    ADD COLUMN segment_start TIMESTAMPTZ,
    ADD COLUMN segment_end TIMESTAMPTZ,
    ADD COLUMN progress VARCHAR(4000),
    ADD CONSTRAINT fk_work_record_focus_session_owner FOREIGN KEY (focus_session_id,user_id) REFERENCES focus_sessions(id,user_id),
    ADD CONSTRAINT work_records_source_check CHECK (source IN ('MANUAL','TASK_COMPLETION','FOCUS_SESSION')),
    ADD CONSTRAINT work_records_task_source_check CHECK (
        (source='MANUAL' AND todo_id IS NULL AND focus_session_id IS NULL) OR
        (source='TASK_COMPLETION' AND todo_id IS NOT NULL AND focus_session_id IS NULL) OR
        (source='FOCUS_SESSION' AND focus_session_id IS NOT NULL)),
    ADD CONSTRAINT ck_work_record_focus_fields CHECK (
        (source='FOCUS_SESSION' AND business_date IS NOT NULL AND focus_ms > 0 AND break_ms >= 0
            AND segment_start IS NOT NULL AND segment_end IS NOT NULL AND segment_end > segment_start)
        OR (source <> 'FOCUS_SESSION' AND business_date IS NULL AND focus_ms IS NULL AND break_ms IS NULL
            AND segment_start IS NULL AND segment_end IS NULL));
CREATE UNIQUE INDEX uq_work_record_focus_day ON work_records(user_id,focus_session_id,business_date)
    WHERE source='FOCUS_SESSION';
