CREATE TABLE community_posts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    type TEXT NOT NULL CHECK (type IN ('DAILY', 'MOMENT', 'BLOG')),
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN', 'HIDDEN')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_revision_id UUID,
    first_published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, owner_id),
    UNIQUE (id, owner_id, type),
    CHECK ((status = 'DRAFT' AND current_revision_id IS NULL AND first_published_at IS NULL)
        OR (status <> 'DRAFT' AND current_revision_id IS NOT NULL AND first_published_at IS NOT NULL))
);

CREATE TABLE community_post_drafts (
    post_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    type TEXT NOT NULL,
    business_date DATE,
    title TEXT NOT NULL DEFAULT '' CHECK (length(title) <= 200),
    summary TEXT NOT NULL DEFAULT '' CHECK (length(summary) <= 500),
    body_markdown TEXT NOT NULL DEFAULT '' CHECK (length(body_markdown) <= 100000),
    source_selection JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(source_selection) = 'array'),
    saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id, owner_id, type) REFERENCES community_posts(id, owner_id, type) ON DELETE RESTRICT,
    CHECK ((type = 'DAILY' AND business_date IS NOT NULL) OR (type <> 'DAILY' AND business_date IS NULL))
);

CREATE TABLE community_post_revisions (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    revision_no INTEGER NOT NULL CHECK (revision_no > 0),
    type TEXT NOT NULL,
    business_date DATE,
    title TEXT NOT NULL CHECK (length(title) <= 200 AND (type = 'MOMENT' OR length(btrim(title)) > 0)),
    summary TEXT NOT NULL DEFAULT '' CHECK (length(summary) <= 500),
    body_markdown TEXT NOT NULL CHECK (length(btrim(body_markdown)) > 0 AND length(body_markdown) <= 100000),
    source_selection JSONB NOT NULL CHECK (jsonb_typeof(source_selection) = 'array'),
    request_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE (post_id, revision_no),
    UNIQUE (post_id, request_id),
    UNIQUE (post_id, owner_id, id),
    FOREIGN KEY (post_id, owner_id, type) REFERENCES community_posts(id, owner_id, type) ON DELETE RESTRICT,
    CHECK ((type = 'DAILY' AND business_date IS NOT NULL) OR (type <> 'DAILY' AND business_date IS NULL))
);

ALTER TABLE community_posts ADD CONSTRAINT fk_community_current_revision
    FOREIGN KEY (id, owner_id, current_revision_id)
    REFERENCES community_post_revisions(post_id, owner_id, id) ON DELETE RESTRICT;

CREATE TABLE community_public_profiles (
    owner_id UUID PRIMARY KEY REFERENCES user_accounts(id) ON DELETE RESTRICT,
    nickname TEXT CHECK (nickname IS NULL OR (length(btrim(nickname)) > 0 AND length(nickname) <= 40)),
    bio TEXT NOT NULL DEFAULT '' CHECK (length(bio) <= 500),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE community_moderation_audit (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    action TEXT NOT NULL CHECK (action = 'HIDE'),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0 AND length(reason) <= 1000),
    revision_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (post_id, owner_id, revision_id)
        REFERENCES community_post_revisions(post_id, owner_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_community_visible ON community_posts(first_published_at DESC, id DESC) WHERE status = 'PUBLISHED';
CREATE INDEX idx_community_owner ON community_posts(owner_id, updated_at DESC, id DESC);
CREATE INDEX idx_community_moderation_post ON community_moderation_audit(post_id, occurred_at DESC, id DESC);
