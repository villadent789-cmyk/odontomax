-- Cambiar el nombre, el DNI o la fecha de nacimiento de un paciente exige la autorización del dueño (administrador general).
-- Quien detecta el error lo solicita con su motivo; el dueño aprueba (y se aplica el cambio) o rechaza.
CREATE TABLE IF NOT EXISTS solicitudes_correccion_paciente (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id               uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    sede_id                   text NOT NULL,
    nombres_anterior          text NOT NULL,
    apellidos_anterior        text NOT NULL,
    dni_anterior              text NOT NULL,
    fecha_nacimiento_anterior date,
    nombres_nuevo             text,
    apellidos_nuevo           text,
    dni_nuevo                 text,
    fecha_nacimiento_nueva    date,
    motivo                    text NOT NULL CHECK (length(btrim(motivo)) > 0),
    estado                    text NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    solicitado_por            uuid NOT NULL,
    solicitado_por_nombre     text NOT NULL,
    solicitado_en             timestamptz NOT NULL DEFAULT now(),
    resuelto_por              uuid,
    resuelto_por_nombre       text,
    resuelto_en               timestamptz,
    comentario_resolucion     text
);

CREATE INDEX IF NOT EXISTS idx_solicitudes_correccion_estado ON solicitudes_correccion_paciente (estado, solicitado_en DESC);
CREATE INDEX IF NOT EXISTS idx_solicitudes_correccion_paciente ON solicitudes_correccion_paciente (paciente_id, solicitado_en DESC);
