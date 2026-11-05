ALTER TABLE pagos_laboratorio
    ADD COLUMN IF NOT EXISTS anulado boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS anulado_por uuid REFERENCES usuarios(id),
    ADD COLUMN IF NOT EXISTS anulado_en timestamptz,
    ADD COLUMN IF NOT EXISTS motivo_anulacion text;

CREATE INDEX IF NOT EXISTS idx_pagos_laboratorio_anulado
    ON pagos_laboratorio (anulado);
