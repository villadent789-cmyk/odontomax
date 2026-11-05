-- Orden de estudio: un doctor de Villadent pide un estudio a Villamax. Villamax ve los datos básicos del paciente
-- (copiados al pedir) y adjunta el resultado; el doctor de origen lo abre desde la ficha del paciente.
CREATE TABLE IF NOT EXISTS ordenes_estudio (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id         uuid NOT NULL REFERENCES pacientes(id),
    sede_origen_id      text NOT NULL REFERENCES sedes(id),
    paciente_nombre     text NOT NULL,
    paciente_dni        text NOT NULL,
    paciente_telefono   text,
    estudio_id          text NOT NULL REFERENCES servicios_web(id),
    estudio_nombre      text NOT NULL,
    notas               text,
    estado              text NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'REALIZADA', 'CANCELADA')),
    solicitado_por      uuid REFERENCES usuarios(id),
    solicitado_por_nombre text NOT NULL,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    resultado_key       text,
    resultado_nombre    text,
    resultado_mime      text,
    resultado_bytes     bigint,
    resultado_por       uuid REFERENCES usuarios(id),
    resultado_en        timestamptz
);
CREATE INDEX IF NOT EXISTS ordenes_estudio_paciente_idx ON ordenes_estudio (paciente_id);
CREATE INDEX IF NOT EXISTS ordenes_estudio_estado_idx ON ordenes_estudio (estado, creado_en DESC);
