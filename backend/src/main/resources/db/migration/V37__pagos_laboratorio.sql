CREATE TABLE IF NOT EXISTS pagos_laboratorio (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id uuid NOT NULL REFERENCES ordenes_laboratorio(id),
    laboratorio_id uuid NOT NULL REFERENCES laboratorios_externos(id),
    sede_id varchar(50) NOT NULL REFERENCES sedes(id),
    monto numeric(10,2) NOT NULL CHECK (monto > 0),
    metodo_pago text NOT NULL CHECK (metodo_pago IN ('EFECTIVO', 'YAPE', 'PLIN')),
    numero_operacion text,
    pagado_en timestamptz NOT NULL DEFAULT now(),
    caja_sesion_id uuid REFERENCES caja_sesiones(id),
    registrado_por uuid REFERENCES usuarios(id),
    notas text
);

CREATE INDEX IF NOT EXISTS idx_pagos_laboratorio_orden
    ON pagos_laboratorio (orden_id);
CREATE INDEX IF NOT EXISTS idx_pagos_laboratorio_laboratorio
    ON pagos_laboratorio (laboratorio_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass
          AND conname = 'caja_movimientos_referencia_tipo_check'
    ) THEN
        ALTER TABLE caja_movimientos
            DROP CONSTRAINT caja_movimientos_referencia_tipo_check;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass
          AND conname = 'caja_movimientos_referencia_tipo_lab_check'
    ) THEN
        ALTER TABLE caja_movimientos
            ADD CONSTRAINT caja_movimientos_referencia_tipo_lab_check
            CHECK (referencia_tipo IS NULL OR referencia_tipo IN (
                'PAGO_TRATAMIENTO', 'GASTO', 'AJUSTE_MANUAL', 'OTRO', 'PAGO_LABORATORIO'
            ));
    END IF;
END $$;
