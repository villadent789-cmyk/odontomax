-- Historia clínica general (ficha de ingreso): se llena una sola vez al ingresar y se puede completar después.
-- Los datos van en un documento jsonb ordenado por secciones; no se consultan por columna.
CREATE TABLE IF NOT EXISTS pacientes_ficha_ingreso (
    paciente_id uuid PRIMARY KEY REFERENCES pacientes(id) ON DELETE CASCADE,
    datos jsonb NOT NULL DEFAULT '{}'::jsonb,
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    actualizado_por uuid,
    actualizado_por_nombre varchar(120)
);

-- Foto de perfil del paciente: se reduce en el navegador (unos 50 KB) y se guarda aparte para no cargarla con el paciente.
CREATE TABLE IF NOT EXISTS paciente_fotos (
    paciente_id uuid PRIMARY KEY REFERENCES pacientes(id) ON DELETE CASCADE,
    contenido bytea NOT NULL,
    content_type varchar(30) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    actualizado_en timestamptz NOT NULL DEFAULT now(),
    actualizado_por varchar(120)
);

ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS foto_actualizada_en timestamptz;
