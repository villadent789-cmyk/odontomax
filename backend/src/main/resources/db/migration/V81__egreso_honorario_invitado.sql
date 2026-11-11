-- El pago a un doctor invitado al terminar su atencion sale de la caja como un egreso mas (referencia HONORARIO_INVITADO).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass AND conname = 'caja_movimientos_referencia_tipo_comision_check'
    ) THEN
        ALTER TABLE caja_movimientos DROP CONSTRAINT caja_movimientos_referencia_tipo_comision_check;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass AND conname = 'caja_movimientos_referencia_tipo_honorario_check'
    ) THEN
        ALTER TABLE caja_movimientos
            ADD CONSTRAINT caja_movimientos_referencia_tipo_honorario_check
            CHECK (referencia_tipo IS NULL OR referencia_tipo IN (
                'PAGO_TRATAMIENTO', 'GASTO', 'AJUSTE_MANUAL', 'OTRO', 'PAGO_LABORATORIO', 'COMISION_RADIOLOGICA', 'HONORARIO_INVITADO'
            ));
    END IF;
END $$;
