ALTER TABLE todo_items
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE work_records
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN todo_id UUID REFERENCES todo_items(id) ON DELETE RESTRICT,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN completion_result TEXT NOT NULL DEFAULT '',
    ADD CONSTRAINT work_records_source_check CHECK (source IN ('MANUAL', 'TASK_COMPLETION')),
    ADD CONSTRAINT work_records_task_source_check CHECK (
        (source = 'MANUAL' AND todo_id IS NULL)
        OR (source = 'TASK_COMPLETION' AND todo_id IS NOT NULL)
    ),
    ADD CONSTRAINT work_records_completion_result_length_check CHECK (char_length(completion_result) <= 4000);

CREATE UNIQUE INDEX uq_work_records_active_task_completion
    ON work_records (todo_id)
    WHERE source = 'TASK_COMPLETION' AND is_active;
CREATE INDEX idx_work_records_todo_history
    ON work_records (todo_id, created_at DESC)
    WHERE source = 'TASK_COMPLETION';

ALTER TABLE task_events DROP CONSTRAINT task_events_event_type_check;
ALTER TABLE task_events
    ADD CONSTRAINT task_events_event_type_check
        CHECK (event_type IN ('CREATED', 'UPDATED', 'COMPLETED', 'REOPENED', 'CANCELLED', 'DELETED', 'COMPLETION_RESULT_UPDATED'));

ALTER TABLE task_events DROP CONSTRAINT task_events_todo_id_fkey;
ALTER TABLE task_events
    ADD CONSTRAINT task_events_todo_id_fkey
        FOREIGN KEY (todo_id) REFERENCES todo_items(id) ON DELETE RESTRICT;

CREATE INDEX idx_todo_items_visible_created_at
    ON todo_items (created_at DESC)
    WHERE deleted_at IS NULL;
