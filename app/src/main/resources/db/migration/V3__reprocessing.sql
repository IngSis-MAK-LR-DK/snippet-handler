-- US 12 y 15: cada cambio de reglas es un job; cada snippet a reprocesar, una tarea con estado.
CREATE TABLE reprocess_job (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    language   VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX reprocess_job_user_idx ON reprocess_job (user_id, created_at DESC);

CREATE TABLE reprocess_task (
    id         UUID PRIMARY KEY,
    job_id     UUID         NOT NULL REFERENCES reprocess_job (id) ON DELETE CASCADE,
    snippet_id UUID         NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    attempts   INTEGER      NOT NULL,
    error      VARCHAR(500),
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX reprocess_task_job_idx ON reprocess_task (job_id, status);
