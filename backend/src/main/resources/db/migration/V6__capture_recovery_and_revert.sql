ALTER TABLE capture_inputs
    ADD COLUMN processing_token UUID,
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

UPDATE capture_inputs
SET lease_expires_at = updated_at
WHERE status = 'PROCESSING';

CREATE INDEX idx_capture_inputs_processing_lease
    ON capture_inputs (lease_expires_at)
    WHERE status = 'PROCESSING';

ALTER TABLE work_records
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT work_records_version_check CHECK (version >= 0);

CREATE TABLE capture_generated_items (
    input_id UUID NOT NULL REFERENCES capture_inputs(id) ON DELETE RESTRICT,
    entity_type VARCHAR(16) NOT NULL CHECK (entity_type IN ('RECORD', 'TASK')),
    entity_id UUID NOT NULL,
    initial_version BIGINT NOT NULL CHECK (initial_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (input_id, entity_type, entity_id)
);

INSERT INTO capture_generated_items (input_id, entity_type, entity_id, initial_version)
SELECT capture_input_id, 'RECORD', id, version
FROM work_records
WHERE capture_input_id IS NOT NULL;

INSERT INTO capture_generated_items (input_id, entity_type, entity_id, initial_version)
SELECT capture_input_id, 'TASK', id, version
FROM todo_items
WHERE capture_input_id IS NOT NULL;

CREATE UNIQUE INDEX uq_capture_generated_items_entity
    ON capture_generated_items (entity_type, entity_id);
