ALTER TABLE todo_items DROP CONSTRAINT todo_items_status_check;

UPDATE todo_items SET status = 'PENDING' WHERE status IN ('OPEN', 'CANCELLED');
UPDATE todo_items SET status = 'COMPLETED' WHERE status = 'DONE';

ALTER TABLE todo_items
    ADD COLUMN notes TEXT NOT NULL DEFAULT '',
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT todo_items_status_check CHECK (status IN ('PENDING', 'COMPLETED')),
    ADD CONSTRAINT todo_items_priority_check CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    ADD CONSTRAINT todo_items_notes_length_check CHECK (char_length(notes) <= 4000),
    ADD CONSTRAINT todo_items_version_check CHECK (version >= 0);

DROP INDEX idx_todo_items_status_due_at;
CREATE INDEX idx_todo_items_status_due_at ON todo_items (status, due_at, created_at DESC);
CREATE INDEX idx_todo_items_priority ON todo_items (priority, created_at DESC);
