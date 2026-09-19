CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'ACTIVE' AND archived_at IS NULL) OR (status = 'ARCHIVED' AND archived_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_projects_active_name ON projects (lower(name)) WHERE status = 'ACTIVE';
CREATE INDEX idx_projects_status_created_at ON projects (status, created_at DESC);

CREATE TABLE capture_inputs (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_capture_inputs_captured_at ON capture_inputs (captured_at DESC);

CREATE TABLE todo_items (
    id UUID PRIMARY KEY,
    project_id UUID REFERENCES projects(id) ON DELETE RESTRICT,
    title VARCHAR(240) NOT NULL CHECK (btrim(title) <> ''),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_todo_items_status_due_at ON todo_items (status, due_at);
CREATE INDEX idx_todo_items_project_id ON todo_items (project_id);

CREATE TABLE work_records (
    id UUID PRIMARY KEY,
    project_id UUID REFERENCES projects(id) ON DELETE RESTRICT,
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_work_records_occurred_at ON work_records (occurred_at DESC);
CREATE INDEX idx_work_records_project_occurred_at ON work_records (project_id, occurred_at DESC);

CREATE TABLE task_events (
    id UUID PRIMARY KEY,
    todo_id UUID NOT NULL REFERENCES todo_items(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('CREATED', 'UPDATED', 'COMPLETED', 'REOPENED', 'CANCELLED')),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_task_events_todo_occurred_at ON task_events (todo_id, occurred_at DESC);

CREATE TABLE reports (
    id UUID PRIMARY KEY,
    report_type VARCHAR(16) NOT NULL CHECK (report_type IN ('DAILY', 'WEEKLY')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (period_end >= period_start),
    UNIQUE (report_type, period_start, period_end)
);
CREATE INDEX idx_reports_period ON reports (report_type, period_start DESC);
