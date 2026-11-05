-- Presupuesto: lo que se puede hacer por un paciente que viene a consulta (a ver qué tratamientos necesita o por una
-- curación, pero con más trabajos pendientes). El paciente acepta todo o solo una parte; lo aceptado se convierte en plan.
CREATE TABLE IF NOT EXISTS presupuestos (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    sede_id varchar(50) NOT NULL REFERENCES sedes(id),
    especialista_id uuid REFERENCES especialistas(id),
    motivo varchar(300),
    notas varchar(500),
    estado varchar(10) NOT NULL DEFAULT 'VIGENTE' CHECK (estado IN ('VIGENTE', 'ACEPTADO', 'RECHAZADO')),
    total numeric(10,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    tratamiento_id uuid REFERENCES tratamientos(id),
    creado_por varchar(120),
    creado_en timestamptz NOT NULL DEFAULT now(),
    resuelto_en timestamptz
);
CREATE INDEX IF NOT EXISTS idx_presupuestos_paciente ON presupuestos(paciente_id, creado_en DESC);

CREATE TABLE IF NOT EXISTS presupuesto_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    presupuesto_id uuid NOT NULL REFERENCES presupuestos(id) ON DELETE CASCADE,
    procedimiento_id varchar(60),
    nombre varchar(200) NOT NULL,
    pieza_fdi integer,
    gravedad varchar(10) CHECK (gravedad IS NULL OR gravedad IN ('LEVE', 'MODERADO', 'SEVERO')),
    cantidad integer NOT NULL DEFAULT 1 CHECK (cantidad >= 1),
    precio_unitario numeric(10,2) NOT NULL CHECK (precio_unitario >= 0),
    aceptado boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_presupuesto_items_presupuesto ON presupuesto_items(presupuesto_id);
