ALTER TABLE citas ADD COLUMN IF NOT EXISTS llegada_en timestamptz;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS inicio_atencion_en timestamptz;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS fin_atencion_en timestamptz;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'citas'::regclass
          AND conname = 'citas_tiempos_atencion_coherentes'
    ) THEN
        ALTER TABLE citas
            ADD CONSTRAINT citas_tiempos_atencion_coherentes CHECK (
                (llegada_en IS NULL OR inicio_atencion_en IS NULL OR llegada_en <= inicio_atencion_en)
                AND (inicio_atencion_en IS NULL OR fin_atencion_en IS NULL OR inicio_atencion_en <= fin_atencion_en)
                AND (llegada_en IS NULL OR fin_atencion_en IS NULL OR llegada_en <= fin_atencion_en)
            ) NOT VALID;
    END IF;
END $$;
