-- Los estudios de Villamax viven en el mismo catalogo de servicios que los tratamientos de Villadent, separados por
-- negocio. La comision es el porcentaje que se lleva el doctor externo que deriva al paciente.
ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS unidad_de_negocio text NOT NULL DEFAULT 'VILLADENT';
ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS comision_porcentaje numeric(5,2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'servicios_web'::regclass AND conname = 'servicios_web_unidad_de_negocio_check'
    ) THEN
        ALTER TABLE servicios_web
            ADD CONSTRAINT servicios_web_unidad_de_negocio_check
            CHECK (unidad_de_negocio IN ('VILLADENT', 'VILLAMAX'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'servicios_web'::regclass AND conname = 'servicios_web_comision_porcentaje_check'
    ) THEN
        ALTER TABLE servicios_web
            ADD CONSTRAINT servicios_web_comision_porcentaje_check
            CHECK (comision_porcentaje IS NULL OR (comision_porcentaje >= 0 AND comision_porcentaje <= 100));
    END IF;
END $$;

-- Estudios habituales de Villamax. Precios y comisiones de EJEMPLO: se ajustan en Administracion > Servicios.
INSERT INTO servicios_web (id, nombre, especialidad, descripcion, duracion_min, precio_desde, activo, orden,
                           unidad_de_negocio, comision_porcentaje)
VALUES
    ('vmx-panoramica', 'Radiografía panorámica', 'Radiología', 'Precio y comisión de ejemplo: ajústalos en Administración.', 15, 60.00, true, 1, 'VILLAMAX', 10.00),
    ('vmx-cefalometrica', 'Radiografía cefalométrica', 'Radiología', 'Precio y comisión de ejemplo: ajústalos en Administración.', 15, 60.00, true, 2, 'VILLAMAX', 10.00),
    ('vmx-periapical', 'Radiografía periapical', 'Radiología', 'Precio y comisión de ejemplo: ajústalos en Administración.', 10, 15.00, true, 3, 'VILLAMAX', 10.00),
    ('vmx-tomografia-3d', 'Tomografía 3D Cone Beam', 'Radiología', 'Precio y comisión de ejemplo: ajústalos en Administración.', 30, 250.00, true, 4, 'VILLAMAX', 10.00)
ON CONFLICT (id) DO NOTHING;

-- El pago de una comision sale de la caja de Villamax como un egreso mas.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass AND conname = 'caja_movimientos_referencia_tipo_lab_check'
    ) THEN
        ALTER TABLE caja_movimientos DROP CONSTRAINT caja_movimientos_referencia_tipo_lab_check;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'caja_movimientos'::regclass AND conname = 'caja_movimientos_referencia_tipo_comision_check'
    ) THEN
        ALTER TABLE caja_movimientos
            ADD CONSTRAINT caja_movimientos_referencia_tipo_comision_check
            CHECK (referencia_tipo IS NULL OR referencia_tipo IN (
                'PAGO_TRATAMIENTO', 'GASTO', 'AJUSTE_MANUAL', 'OTRO', 'PAGO_LABORATORIO', 'COMISION_RADIOLOGICA'
            ));
    END IF;
END $$;
