-- =============================================================================
-- V6: Tratamientos, Presupuestos, Pagos, Arqueo de Caja y Movimientos
-- =============================================================================

-- 1. Extender tratamientos para aceptación de presupuesto y descuentos
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS aceptado_por_paciente boolean NOT NULL DEFAULT false;
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS fecha_aceptacion timestamptz;
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS descuento_total numeric(10,2) NOT NULL DEFAULT 0;
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS descuento_autorizado_por uuid REFERENCES usuarios(id);

-- 2. Items detallados de planes de tratamiento y presupuestos
CREATE TABLE IF NOT EXISTS tratamiento_items (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tratamiento_id          uuid NOT NULL REFERENCES tratamientos(id) ON DELETE CASCADE,
    procedimiento_id        text REFERENCES catalogo_procedimientos(id),
    nombre_procedimiento    text NOT NULL,
    pieza_fdi               smallint,
    precio_base             numeric(10,2) NOT NULL CHECK (precio_base >= 0),
    cantidad                integer NOT NULL DEFAULT 1 CHECK (cantidad > 0),
    descuento               numeric(10,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0),
    descuento_autorizado_por uuid REFERENCES usuarios(id),
    precio_final            numeric(10,2) NOT NULL CHECK (precio_final >= 0),
    estado                  text NOT NULL DEFAULT 'PLANIFICADO' CHECK (estado IN ('PLANIFICADO','EN_PROCESO','COMPLETADO','CANCELADO')),
    observaciones           text,
    creado_en               timestamptz NOT NULL DEFAULT now(),
    actualizado_en          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tratamiento_items_tratamiento ON tratamiento_items (tratamiento_id);

-- 3. Extender pagos para asociar a sesión de caja y comprobantes
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS caja_sesion_id uuid REFERENCES caja_sesiones(id);
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS comprobante_tipo text DEFAULT 'RECIBO' CHECK (comprobante_tipo IN ('RECIBO','BOLETA','FACTURA'));
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS comprobante_serie text;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS comprobante_numero text;
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS comprobante_hash text;

-- 4. Movimientos detallados de caja diaria (arqueo esperado vs contado)
CREATE TABLE IF NOT EXISTS caja_movimientos (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id               uuid NOT NULL REFERENCES caja_sesiones(id) ON DELETE CASCADE,
    tipo                    text NOT NULL CHECK (tipo IN ('INGRESO','EGRESO')),
    concepto                text NOT NULL,
    monto                   numeric(10,2) NOT NULL CHECK (monto > 0),
    metodo_pago             text NOT NULL DEFAULT 'EFECTIVO' CHECK (metodo_pago IN ('EFECTIVO','YAPE')),
    referencia_tipo         text CHECK (referencia_tipo IN ('PAGO_TRATAMIENTO','GASTO','AJUSTE_MANUAL','OTRO')),
    referencia_id           uuid,
    registrado_por          uuid NOT NULL REFERENCES usuarios(id),
    registrado_en           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_caja_movimientos_sesion ON caja_movimientos (sesion_id, registrado_en ASC);

-- 5. Extender gastos para vinculación con sesión de caja y comprobante en Cloudflare R2
ALTER TABLE gastos ADD COLUMN IF NOT EXISTS caja_sesion_id uuid REFERENCES caja_sesiones(id);
ALTER TABLE gastos ADD COLUMN IF NOT EXISTS comprobante_key text;
