ALTER TABLE reports DROP CONSTRAINT reports_report_type_period_start_period_end_key;

ALTER TABLE reports
    ADD COLUMN request_id UUID,
    ADD COLUMN status VARCHAR(16),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN edited_at TIMESTAMPTZ,
    ADD COLUMN error_message VARCHAR(500),
    ADD COLUMN processing_token UUID,
    ADD COLUMN zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai';

UPDATE reports
SET request_id = gen_random_uuid(), status = 'SUCCEEDED'
WHERE request_id IS NULL;

ALTER TABLE reports
    ALTER COLUMN request_id SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN content SET DEFAULT '',
    ADD CONSTRAINT reports_status_check CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    ADD CONSTRAINT reports_version_check CHECK (version >= 0);

CREATE UNIQUE INDEX uq_reports_request_id ON reports (request_id);
CREATE INDEX idx_reports_type_period_versions
    ON reports (report_type, period_start DESC, created_at DESC);

CREATE TABLE report_sources (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES reports(id) ON DELETE RESTRICT,
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('RECORD', 'TASK')),
    entity_id UUID NOT NULL,
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    project_id UUID,
    project_name VARCHAR(120),
    source_status VARCHAR(24),
    source_time TIMESTAMPTZ NOT NULL,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (report_id, source_type, entity_id)
);

CREATE INDEX idx_report_sources_report_id ON report_sources (report_id, created_at, id);
