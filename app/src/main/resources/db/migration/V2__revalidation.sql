CREATE TABLE revalidation_run (
    id               UUID PRIMARY KEY,
    language         VARCHAR(64)  NOT NULL,
    language_version VARCHAR(32)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    total            INTEGER      NOT NULL,
    processed        INTEGER      NOT NULL,
    failed           INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);
