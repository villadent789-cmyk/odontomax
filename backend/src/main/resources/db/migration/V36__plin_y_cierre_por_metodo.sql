-- Amplía los medios de pago permitidos y conserva la diferencia generada para efectivo.
DO $$
DECLARE
    restriccion record;
BEGIN
    FOR restriccion IN
        SELECT c.conname, c.conrelid::regclass AS tabla
        FROM pg_constraint c
        WHERE c.contype = 'c'
          AND c.conrelid IN ('pagos'::regclass, 'caja_movimientos'::regclass)
          AND pg_get_constraintdef(c.oid) ILIKE '%metodo_pago%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', restriccion.tabla, restriccion.conname);
    END LOOP;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pagos_metodo_pago_plin_check'
          AND conrelid = 'pagos'::regclass
    ) THEN
        ALTER TABLE pagos ADD CONSTRAINT pagos_metodo_pago_plin_check
            CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'caja_movimientos_metodo_pago_plin_check'
          AND conrelid = 'caja_movimientos'::regclass
    ) THEN
        ALTER TABLE caja_movimientos ADD CONSTRAINT caja_movimientos_metodo_pago_plin_check
            CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN'));
    END IF;
END $$;

ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_esperado_yape numeric(10,2);
ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_contado_yape numeric(10,2);
ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_esperado_plin numeric(10,2);
ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_contado_plin numeric(10,2);
