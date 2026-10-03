CREATE TABLE community_attachments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    request_id UUID NOT NULL,
    object_key TEXT NOT NULL UNIQUE CHECK (object_key ~ '^community/attachments/[0-9a-f-]{36}$'),
    original_filename TEXT NOT NULL CHECK (length(original_filename) BETWEEN 1 AND 255),
    verified_content_type TEXT NOT NULL CHECK (verified_content_type IN ('image/jpeg','image/png','image/webp','application/pdf','text/markdown; charset=UTF-8')),
    actual_size BIGINT NOT NULL CHECK (actual_size >= 0 AND actual_size <= 20971520),
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    state TEXT NOT NULL CHECK (state IN ('UPLOADING','READY','FAILED','DELETING','DELETE_FAILED','DELETED')),
    attempt_token UUID NOT NULL,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    safe_failure_code TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (post_id, request_id),
    UNIQUE (post_id, owner_id, id),
    FOREIGN KEY (post_id, owner_id) REFERENCES community_posts(id, owner_id) ON DELETE RESTRICT,
    CHECK ((verified_content_type LIKE 'image/%' AND actual_size > 0 AND actual_size <= 5242880)
        OR (verified_content_type = 'application/pdf' AND actual_size > 0)
        OR (verified_content_type = 'text/markdown; charset=UTF-8' AND actual_size <= 1048576))
);

CREATE TABLE community_draft_attachments (
    post_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (post_id, attachment_id),
    UNIQUE (post_id, position),
    FOREIGN KEY (post_id, owner_id, attachment_id) REFERENCES community_attachments(post_id, owner_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (post_id, owner_id) REFERENCES community_posts(id, owner_id) ON DELETE RESTRICT
);

CREATE TABLE community_revision_attachments (
    revision_id UUID NOT NULL,
    post_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (revision_id, attachment_id),
    UNIQUE (revision_id, position),
    FOREIGN KEY (post_id, owner_id, attachment_id) REFERENCES community_attachments(post_id, owner_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (post_id, owner_id, revision_id) REFERENCES community_post_revisions(post_id, owner_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_community_attachment_owner_post ON community_attachments(owner_id, post_id, created_at, id);
CREATE INDEX idx_community_revision_attachment ON community_revision_attachments(attachment_id);
