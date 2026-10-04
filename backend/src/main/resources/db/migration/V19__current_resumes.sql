CREATE TABLE resume_objects (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    storage_key TEXT NOT NULL UNIQUE,
    original_filename TEXT,
    content_type TEXT NOT NULL CHECK (content_type = 'text/markdown; charset=UTF-8'),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 0 AND 1048576),
    sha256 TEXT NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    status TEXT NOT NULL CHECK (status IN ('UPLOADING','READY','FAILED','DELETING','DELETE_FAILED','DELETED')),
    upload_token UUID NOT NULL,
    operation_token UUID NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    cleanup_after TIMESTAMPTZ NOT NULL,
    io_uncertain BOOLEAN NOT NULL DEFAULT TRUE,
    safe_failure_code TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id,id),
    UNIQUE (user_id,request_id),
    CHECK (storage_key = 'interview/resumes/' || user_id::text || '/' || id::text || '.md'),
    CHECK (status NOT IN ('UPLOADING','READY') OR original_filename IS NOT NULL)
);

CREATE TABLE user_resumes (
    user_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    markdown_text TEXT,
    source_kind TEXT CHECK (source_kind IN ('PASTE','MD_FILE')),
    current_object_id UUID,
    content_sha256 TEXT CHECK (content_sha256 ~ '^[a-f0-9]{64}$'),
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (user_id,current_object_id) REFERENCES resume_objects(user_id,id) ON DELETE RESTRICT,
    CHECK ((markdown_text IS NULL AND source_kind IS NULL AND current_object_id IS NULL AND content_sha256 IS NULL)
        OR (markdown_text IS NOT NULL AND CHAR_LENGTH(markdown_text) <= 20000 AND content_sha256 IS NOT NULL AND source_kind IS NOT NULL
            AND ((source_kind = 'PASTE' AND current_object_id IS NULL) OR (source_kind = 'MD_FILE' AND current_object_id IS NOT NULL))))
);

CREATE TABLE resume_write_receipts (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    operation TEXT NOT NULL CHECK (operation IN ('PUT','IMPORT','DELETE')),
    request_id UUID NOT NULL,
    payload_hash TEXT NOT NULL CHECK (payload_hash ~ '^[a-f0-9]{64}$'),
    state TEXT NOT NULL CHECK (state IN ('UPLOADING','SUCCEEDED','FAILED')),
    result_version BIGINT CHECK (result_version >= 0),
    object_id UUID,
    safe_failure_code TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id,operation,request_id),
    FOREIGN KEY (user_id,object_id) REFERENCES resume_objects(user_id,id) ON DELETE RESTRICT,
    CHECK (state <> 'SUCCEEDED' OR result_version IS NOT NULL),
    CHECK (operation <> 'IMPORT' OR object_id IS NOT NULL)
);
CREATE INDEX ix_resume_objects_maintenance ON resume_objects(user_id,status,cleanup_after);
