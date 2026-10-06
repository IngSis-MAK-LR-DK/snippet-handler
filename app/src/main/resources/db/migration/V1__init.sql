CREATE TABLE snippet (
    id               UUID PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    language         VARCHAR(64)  NOT NULL,
    language_version VARCHAR(32)  NOT NULL,
    owner_id         VARCHAR(255) NOT NULL,
    compliance       VARCHAR(32)  NOT NULL,
    lint_violations  JSONB        NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_snippet_owner ON snippet (owner_id);
CREATE INDEX idx_snippet_name ON snippet (name);

CREATE TABLE test_case (
    id               UUID PRIMARY KEY,
    snippet_id       UUID         NOT NULL REFERENCES snippet (id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    inputs           JSONB        NOT NULL,
    expected_outputs JSONB        NOT NULL,
    last_status      VARCHAR(32)  NOT NULL,
    last_run_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_test_case_snippet ON test_case (snippet_id);

CREATE TABLE rule_config (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    name       VARCHAR(128) NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    rule_value VARCHAR(255),
    CONSTRAINT uq_rule_config UNIQUE (user_id, type, name)
);
