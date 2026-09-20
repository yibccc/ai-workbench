ALTER TABLE reports
    ADD COLUMN previous_report_id UUID REFERENCES reports(id) ON DELETE RESTRICT,
    ADD COLUMN manual_additions TEXT NOT NULL DEFAULT '',
    ADD COLUMN manual_edited_at TIMESTAMPTZ;

ALTER TABLE report_sources
    ADD COLUMN source_role VARCHAR(24);

UPDATE report_sources
SET source_role = CASE source_type
    WHEN 'RECORD' THEN 'DAILY_RECORD'
    ELSE 'DAILY_TASK'
END
WHERE source_role IS NULL;

ALTER TABLE report_sources
    ALTER COLUMN source_role SET NOT NULL,
    ADD CONSTRAINT report_sources_role_check CHECK (source_role IN (
        'DAILY_RECORD', 'DAILY_TASK', 'WEEK_RECORD', 'CURRENT_TASK', 'NEXT_WEEK_TASK'
    ));

ALTER TABLE report_sources DROP CONSTRAINT report_sources_report_id_source_type_entity_id_key;
ALTER TABLE report_sources
    ADD CONSTRAINT report_sources_report_role_entity_key UNIQUE (report_id, source_role, entity_id);

CREATE INDEX idx_reports_previous_report_id ON reports (previous_report_id);

COMMENT ON COLUMN reports.period_end IS
    'Exclusive end date for WEEKLY reports; DAILY legacy rows retain period_start=period_end.';
COMMENT ON COLUMN reports.manual_additions IS
    'User-authored text stored separately from AI content and source markers.';
