-- Pacientes que esperan un hueco en la agenda (sin cita todavía). Cuando una cita se cancela o no asiste y queda un
-- hueco libre, recepción ve a quién ofrecérselo.
CREATE TABLE IF NOT EXISTS lista_espera_huecos (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sede_id          text NOT NULL REFERENCES sedes(id),
    paciente_id      uuid NOT NULL REFERENCES pacientes(id),
    paciente_nombre  text NOT NULL,
    paciente_telefono text,
    especialista_id  uuid REFERENCES especialistas(id),
    motivo           text NOT NULL,
    notas            text,
    estado           text NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'OFRECIDA', 'AGENDADA', 'CANCELADA')),
    creado_por       uuid REFERENCES usuarios(id),
    creado_en        timestamptz NOT NULL DEFAULT now(),
    actualizado_en   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS lista_espera_huecos_sede_idx ON lista_espera_huecos (sede_id, estado, creado_en);
