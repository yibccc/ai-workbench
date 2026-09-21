ALTER TABLE reports
    ADD COLUMN error_code VARCHAR(40),
    ADD COLUMN error_stage VARCHAR(40),
    ADD COLUMN source_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE reports
    ADD CONSTRAINT reports_source_count_nonnegative CHECK (source_count >= 0);

COMMENT ON COLUMN reports.error_code IS 'Sanitized report generation failure category.';
COMMENT ON COLUMN reports.error_stage IS 'Sanitized pipeline stage where generation failed.';
COMMENT ON COLUMN reports.source_count IS 'Frozen source count used by this report version.';
