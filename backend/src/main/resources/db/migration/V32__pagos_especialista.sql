-- Atribución de cada cobro al doctor que atendió: alimenta metas y comisiones por doctor sin depender del tratamiento.
ALTER TABLE pagos ADD COLUMN IF NOT EXISTS especialista_id uuid REFERENCES especialistas(id);

UPDATE pagos p SET especialista_id = t.especialista_id
FROM tratamientos t
WHERE p.tratamiento_id = t.id AND p.especialista_id IS NULL AND t.especialista_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pagos_especialista_pagado ON pagos (especialista_id, pagado_en);
