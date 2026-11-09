-- La revalidacion por version de lenguaje pasa a ser un tipo mas de job de reproceso: se va
-- revalidation_run y reprocess_job guarda la version destino. Una revalidacion alcanza a los
-- snippets de todos los duenios, asi que el usuario pasa a ser opcional.
ALTER TABLE reprocess_job ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE reprocess_job ADD COLUMN language_version VARCHAR(32);

DROP TABLE revalidation_run;

-- Un job nace sin tareas: "expanded" dice si ya se escribieron las de sus snippets, para no dar
-- por terminado un job al que todavia no se le armaron.
ALTER TABLE reprocess_job ADD COLUMN expanded BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE reprocess_job ALTER COLUMN expanded SET DEFAULT FALSE;
