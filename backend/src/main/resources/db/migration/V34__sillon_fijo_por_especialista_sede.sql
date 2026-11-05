ALTER TABLE especialista_sedes
    ADD COLUMN IF NOT EXISTS sillon_numero SMALLINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_especialista_sedes_sillon_numero'
          AND conrelid = 'especialista_sedes'::regclass
    ) THEN
        ALTER TABLE especialista_sedes
            ADD CONSTRAINT ck_especialista_sedes_sillon_numero
            CHECK (sillon_numero IS NULL OR sillon_numero > 0);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_especialista_sedes_sillon
    ON especialista_sedes (sede_id, sillon_numero)
    WHERE sillon_numero IS NOT NULL;

UPDATE especialista_sedes
SET sillon_numero = CASE especialista_id
    WHEN '10000000-0000-0000-0000-000000000001' THEN 1
    WHEN '10000000-0000-0000-0000-000000000002' THEN 2
END
WHERE especialista_id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002'
);
