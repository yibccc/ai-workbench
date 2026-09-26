-- Existing business rows have no trustworthy owner. Stop instead of assigning them to an account.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM projects) OR EXISTS (SELECT 1 FROM capture_inputs)
       OR EXISTS (SELECT 1 FROM todo_items) OR EXISTS (SELECT 1 FROM work_records)
       OR EXISTS (SELECT 1 FROM reports) THEN
        RAISE EXCEPTION 'V14 requires an empty business database; verify the intended cleanup target before migration';
    END IF;
END $$;

ALTER TABLE projects ADD COLUMN user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT;
ALTER TABLE capture_inputs ADD COLUMN user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT;
ALTER TABLE todo_items ADD COLUMN user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT;
ALTER TABLE work_records ADD COLUMN user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT;
ALTER TABLE reports ADD COLUMN user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT;

ALTER TABLE projects ADD CONSTRAINT uq_projects_id_user UNIQUE (id, user_id);
ALTER TABLE capture_inputs ADD CONSTRAINT uq_capture_inputs_id_user UNIQUE (id, user_id);
ALTER TABLE todo_items ADD CONSTRAINT uq_todo_items_id_user UNIQUE (id, user_id);
ALTER TABLE reports ADD CONSTRAINT uq_reports_id_user UNIQUE (id, user_id);

ALTER TABLE todo_items ADD CONSTRAINT fk_todo_project_owner
    FOREIGN KEY (project_id, user_id) REFERENCES projects(id, user_id);
ALTER TABLE todo_items ADD CONSTRAINT fk_todo_capture_owner
    FOREIGN KEY (capture_input_id, user_id) REFERENCES capture_inputs(id, user_id);
ALTER TABLE work_records ADD CONSTRAINT fk_record_project_owner
    FOREIGN KEY (project_id, user_id) REFERENCES projects(id, user_id);
ALTER TABLE work_records ADD CONSTRAINT fk_record_capture_owner
    FOREIGN KEY (capture_input_id, user_id) REFERENCES capture_inputs(id, user_id);
ALTER TABLE work_records ADD CONSTRAINT fk_record_todo_owner
    FOREIGN KEY (todo_id, user_id) REFERENCES todo_items(id, user_id);
ALTER TABLE reports ADD CONSTRAINT fk_report_previous_owner
    FOREIGN KEY (previous_report_id, user_id) REFERENCES reports(id, user_id);

DROP INDEX uq_projects_active_name;
CREATE UNIQUE INDEX uq_projects_active_name ON projects (user_id, lower(name)) WHERE status = 'ACTIVE';
DROP INDEX uq_capture_inputs_client_request_id;
CREATE UNIQUE INDEX uq_capture_inputs_client_request_id ON capture_inputs (user_id, client_request_id);
DROP INDEX uq_reports_request_id;
CREATE UNIQUE INDEX uq_reports_request_id ON reports (user_id, request_id);

CREATE INDEX idx_projects_owner_status_created ON projects (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_inputs_owner_status_created ON capture_inputs (user_id, status, created_at DESC, id DESC);
CREATE INDEX idx_tasks_owner_created ON todo_items (user_id, created_at DESC, id DESC);
CREATE INDEX idx_records_owner_occurred ON work_records (user_id, occurred_at DESC, id DESC);
CREATE INDEX idx_reports_owner_type_period ON reports (user_id, report_type, period_start DESC, created_at DESC, id DESC);
