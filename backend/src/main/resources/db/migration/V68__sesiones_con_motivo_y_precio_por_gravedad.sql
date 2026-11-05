-- Las sesiones de un plan se pueden modificar (el doctor sabe cuántas hacen falta, pero a veces el tiempo no alcanza y el
-- paciente se retira con menos), siempre con un motivo que queda registrado.
CREATE TABLE IF NOT EXISTS tratamiento_sesiones_cambios (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tratamiento_id uuid NOT NULL REFERENCES tratamientos(id) ON DELETE CASCADE,
    sesiones_antes integer NOT NULL,
    sesiones_despues integer NOT NULL CHECK (sesiones_despues >= 1),
    motivo varchar(300) NOT NULL,
    usuario_id uuid,
    usuario_nombre varchar(120),
    creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sesiones_cambios_tratamiento ON tratamiento_sesiones_cambios(tratamiento_id, creado_en);

-- El costo por pieza depende del tratamiento y de su gravedad (leve, moderado o severo): así el doctor explica por qué
-- cobra más por un diente muy dañado que por uno que solo necesita un retoque.
ALTER TABLE tratamiento_items ADD COLUMN IF NOT EXISTS gravedad varchar(10)
    CHECK (gravedad IS NULL OR gravedad IN ('LEVE', 'MODERADO', 'SEVERO'));

ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS precio_leve numeric(10,2) CHECK (precio_leve IS NULL OR precio_leve >= 0);
ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS precio_moderado numeric(10,2) CHECK (precio_moderado IS NULL OR precio_moderado >= 0);
ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS precio_severo numeric(10,2) CHECK (precio_severo IS NULL OR precio_severo >= 0);
