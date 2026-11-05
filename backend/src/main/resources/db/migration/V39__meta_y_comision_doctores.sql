-- Meta mensual de cobranza por doctor y comision sobre el excedente. Solo informativas para el dueño.
ALTER TABLE especialistas ADD COLUMN IF NOT EXISTS meta_mensual numeric(10,2);
ALTER TABLE especialistas ADD COLUMN IF NOT EXISTS comision_excedente_porcentaje numeric(5,2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'especialistas'::regclass
          AND conname = 'especialistas_meta_mensual_check'
    ) THEN
        ALTER TABLE especialistas
            ADD CONSTRAINT especialistas_meta_mensual_check
            CHECK (meta_mensual IS NULL OR meta_mensual >= 0);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'especialistas'::regclass
          AND conname = 'especialistas_comision_excedente_check'
    ) THEN
        ALTER TABLE especialistas
            ADD CONSTRAINT especialistas_comision_excedente_check
            CHECK (comision_excedente_porcentaje IS NULL
                   OR (comision_excedente_porcentaje >= 0 AND comision_excedente_porcentaje <= 100));
    END IF;
END $$;
