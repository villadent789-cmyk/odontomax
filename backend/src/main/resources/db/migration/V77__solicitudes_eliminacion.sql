-- Eliminar una radiografía o un paciente exige la autorización del dueño (administrador general).
-- Quien lo necesita lo solicita con su motivo; el dueño aprueba (y se elimina) o rechaza.
-- Sin clave foránea al objetivo: al aprobar, el objetivo desaparece y la solicitud debe quedar como constancia.
CREATE TABLE IF NOT EXISTS solicitudes_eliminacion (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                  text NOT NULL CHECK (tipo IN ('RADIOGRAFIA', 'PACIENTE')),
    objetivo_id           uuid NOT NULL,
    paciente_id           uuid NOT NULL,
    sede_id               text NOT NULL,
    descripcion           text NOT NULL,
    motivo                text NOT NULL CHECK (length(btrim(motivo)) >= 5),
    estado                text NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    solicitado_por        uuid NOT NULL,
    solicitado_por_nombre text NOT NULL,
    solicitado_en         timestamptz NOT NULL DEFAULT now(),
    resuelto_por          uuid,
    resuelto_por_nombre   text,
    resuelto_en           timestamptz,
    comentario_resolucion text
);

CREATE INDEX IF NOT EXISTS idx_solicitudes_eliminacion_estado ON solicitudes_eliminacion (estado, solicitado_en DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitudes_eliminacion_pendiente
    ON solicitudes_eliminacion (tipo, objetivo_id) WHERE estado = 'PENDIENTE';
