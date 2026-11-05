-- Cronograma de cuotas de un plan (p. ej. ortodoncia). Lo pagado no se guarda aquí: se reparte en orden desde los cobros.
CREATE TABLE IF NOT EXISTS plan_cuotas (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tratamiento_id  uuid NOT NULL REFERENCES tratamientos(id) ON DELETE CASCADE,
    numero          smallint NOT NULL CHECK (numero >= 0),
    concepto        text NOT NULL,
    vence_en        date NOT NULL,
    monto           numeric(10,2) NOT NULL CHECK (monto > 0),
    UNIQUE (tratamiento_id, numero)
);
CREATE INDEX IF NOT EXISTS plan_cuotas_vencimiento_idx ON plan_cuotas (vence_en);

-- Precio acordado: el costo del plan puede diferir del catálogo (rangos), dejando constancia de por qué y quién.
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS precio_catalogo numeric(10,2);
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS motivo_ajuste_precio text;
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS ajuste_precio_por uuid REFERENCES usuarios(id);
