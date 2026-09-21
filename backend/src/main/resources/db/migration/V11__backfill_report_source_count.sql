UPDATE reports r
SET source_count = (
    SELECT count(*)
    FROM report_sources rs
    WHERE rs.report_id = r.id
)
WHERE r.source_count = 0
  AND EXISTS (SELECT 1 FROM report_sources rs WHERE rs.report_id = r.id);

