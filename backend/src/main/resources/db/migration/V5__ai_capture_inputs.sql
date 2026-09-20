ALTER TABLE capture_inputs
    ADD COLUMN client_request_id VARCHAR(120),
    ADD COLUMN reference_at TIMESTAMPTZ,
    ADD COLUMN zone_id VARCHAR(64),
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN error_message VARCHAR(500),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1;

UPDATE capture_inputs
SET client_request_id = 'legacy-' || id::text,
    reference_at = captured_at,
    zone_id = 'Asia/Shanghai',
    status = 'SUCCEEDED',
    completed_at = created_at;

ALTER TABLE capture_inputs
    ALTER COLUMN client_request_id SET NOT NULL,
    ALTER COLUMN reference_at SET NOT NULL,
    ALTER COLUMN zone_id SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT capture_inputs_status_check
        CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'REVERTED')),
    ADD CONSTRAINT capture_inputs_attempt_count_check CHECK (attempt_count > 0);

CREATE UNIQUE INDEX uq_capture_inputs_client_request_id ON capture_inputs (client_request_id);
CREATE INDEX idx_capture_inputs_status_created_at ON capture_inputs (status, created_at DESC);

ALTER TABLE work_records
    ADD COLUMN capture_input_id UUID REFERENCES capture_inputs(id) ON DELETE RESTRICT;
ALTER TABLE todo_items
    ADD COLUMN capture_input_id UUID REFERENCES capture_inputs(id) ON DELETE RESTRICT;

CREATE INDEX idx_work_records_capture_input_id ON work_records (capture_input_id)
    WHERE capture_input_id IS NOT NULL;
CREATE INDEX idx_todo_items_capture_input_id ON todo_items (capture_input_id)
    WHERE capture_input_id IS NOT NULL;
