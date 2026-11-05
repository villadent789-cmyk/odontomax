-- Descuento por recomendación: cuando un paciente nuevo dice quién lo recomendó, el paciente que lo recomendó queda con un
-- descuento pendiente para su próximo tratamiento. El monto lo decide el doctor; el sistema solo avisa y lleva la cuenta.
CREATE TABLE IF NOT EXISTS descuentos_recomendacion (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    referido_id uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    estado varchar(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APLICADO')),
    creado_en timestamptz NOT NULL DEFAULT now(),
    aplicado_en timestamptz,
    aplicado_por varchar(120),
    tratamiento_id uuid REFERENCES tratamientos(id) ON DELETE SET NULL,
    CONSTRAINT uq_descuento_recomendacion_referido UNIQUE (referido_id),
    CONSTRAINT ck_descuento_recomendacion_distintos CHECK (paciente_id <> referido_id)
);
CREATE INDEX IF NOT EXISTS idx_descuentos_recomendacion_paciente ON descuentos_recomendacion(paciente_id, estado);
