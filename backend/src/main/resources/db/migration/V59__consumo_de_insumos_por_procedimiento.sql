-- Consumo de insumos por procedimiento (solo Villadent): receta de cada servicio y registro de lo descontado por cita.

-- Qué insumos y cuántas unidades consume cada servicio.
CREATE TABLE IF NOT EXISTS servicio_insumos (
    servicio_id  text    NOT NULL REFERENCES servicios_web(id) ON DELETE CASCADE,
    producto_id  uuid    NOT NULL REFERENCES productos_inventario(id),
    cantidad     integer NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (servicio_id, producto_id)
);

-- Lo que se descontó al atender cada cita. La clave única (cita, producto) impide descontar dos veces.
-- Si el stock no alcanzaba, cantidad_descontada < cantidad_requerida y queda una alerta para el administrador.
CREATE TABLE IF NOT EXISTS consumos_insumos (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    cita_id             uuid        NOT NULL REFERENCES citas(id),
    producto_id         uuid        NOT NULL REFERENCES productos_inventario(id),
    sede_id             text        NOT NULL REFERENCES sedes(id),
    cantidad_requerida  integer     NOT NULL CHECK (cantidad_requerida > 0),
    cantidad_descontada integer     NOT NULL CHECK (cantidad_descontada >= 0),
    faltante_resuelto   boolean     NOT NULL DEFAULT false,
    resuelto_por        uuid        REFERENCES usuarios(id),
    resuelto_en         timestamptz,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (cita_id, producto_id),
    CHECK (cantidad_descontada <= cantidad_requerida)
);
CREATE INDEX IF NOT EXISTS consumos_insumos_alerta_idx ON consumos_insumos (sede_id) WHERE cantidad_descontada < cantidad_requerida AND NOT faltante_resuelto;

-- El movimiento de inventario queda ligado a la cita que lo originó.
ALTER TABLE inventario_movimientos ADD COLUMN IF NOT EXISTS cita_id uuid REFERENCES citas(id);
