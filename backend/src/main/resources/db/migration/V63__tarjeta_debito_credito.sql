-- Cobros con tarjeta de débito o de crédito. En el cierre de caja ambas se cuadran juntas contra el reporte del POS.
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

ALTER TABLE pagos ADD CONSTRAINT pagos_metodo_pago_check
    CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN', 'TARJETA_DEBITO', 'TARJETA_CREDITO'));
ALTER TABLE caja_movimientos ADD CONSTRAINT caja_movimientos_metodo_pago_check
    CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN', 'TARJETA_DEBITO', 'TARJETA_CREDITO'));

ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_esperado_tarjeta numeric(10,2);
ALTER TABLE caja_sesiones ADD COLUMN IF NOT EXISTS monto_contado_tarjeta numeric(10,2);
