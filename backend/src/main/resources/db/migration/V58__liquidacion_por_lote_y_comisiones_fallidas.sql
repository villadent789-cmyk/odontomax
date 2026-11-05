-- Liquidación por lote: las comisiones pagadas juntas comparten un mismo egreso de caja.
ALTER TABLE comisiones_radiologicas ADD COLUMN IF NOT EXISTS lote_liquidacion_id uuid;
CREATE INDEX IF NOT EXISTS comisiones_lote_idx ON comisiones_radiologicas (lote_liquidacion_id) WHERE lote_liquidacion_id IS NOT NULL;

-- Comisiones que no se pudieron generar al cobrar un estudio: quedan a la vista para reintentarlas.
CREATE TABLE IF NOT EXISTS comisiones_fallidas (
    cita_id          uuid PRIMARY KEY REFERENCES citas(id) ON DELETE CASCADE,
    sede_id          text NOT NULL,
    paciente_nombre  text,
    estudio          text,
    motivo           text NOT NULL,
    intentos         integer NOT NULL DEFAULT 1,
    creado_en        timestamptz NOT NULL DEFAULT now(),
    actualizado_en   timestamptz NOT NULL DEFAULT now()
);
