-- =============================================================================
-- V7: Comprobantes Correlativos Persistentes, Triggers de Inmutabilidad y RLS
-- =============================================================================

-- 1. Tabla de Correlativos Persistentes por Serie de Comprobante
CREATE TABLE IF NOT EXISTS comprobante_correlativos (
    serie                   varchar(10) PRIMARY KEY,
    ultimo_numero           bigint NOT NULL DEFAULT 0,
    actualizado_en          timestamptz NOT NULL DEFAULT now()
);

-- Serie de recibo interno compartida por todas las sedes
INSERT INTO comprobante_correlativos (serie, ultimo_numero, actualizado_en)
VALUES
    ('R001', 0, now())
ON CONFLICT (serie) DO NOTHING;

-- 2. Función y Triggers para Proteger Registros Médicos y de Auditoría Inmutables
CREATE OR REPLACE FUNCTION trg_bloquear_modificacion_inmutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Registro inmutable: No está permitido modificar ni eliminar registros en la tabla % por normativa legal sanitaria.', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_inmutable_notas_evolucion ON notas_evolucion;
CREATE TRIGGER trg_inmutable_notas_evolucion
BEFORE UPDATE OR DELETE ON notas_evolucion
FOR EACH ROW EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();

DROP TRIGGER IF EXISTS trg_inmutable_antecedentes ON antecedentes_historial;
CREATE TRIGGER trg_inmutable_antecedentes
BEFORE UPDATE OR DELETE ON antecedentes_historial
FOR EACH ROW EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();

DROP TRIGGER IF EXISTS trg_inmutable_consentimientos ON consentimientos;
CREATE TRIGGER trg_inmutable_consentimientos
BEFORE UPDATE OR DELETE ON consentimientos
FOR EACH ROW EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();

DROP TRIGGER IF EXISTS trg_inmutable_accesos ON historia_clinica_accesos;
CREATE TRIGGER trg_inmutable_accesos
BEFORE UPDATE OR DELETE ON historia_clinica_accesos
FOR EACH ROW EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();

-- 3. Habilitación de Row Level Security (RLS) en tablas V5, V6 y V7
ALTER TABLE IF EXISTS notas_evolucion ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS antecedentes_historial ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS plantillas_consentimiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS consentimientos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS historia_clinica_accesos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS odontograma_registros ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS tratamiento_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS tratamientos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS pagos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS caja_sesiones ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS caja_movimientos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS gastos ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS comprobante_correlativos ENABLE ROW LEVEL SECURITY;

-- 4. Revocación de accesos directos anónimos y autenticados (Supabase PostgREST bypass)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON TABLE notas_evolucion, antecedentes_historial, plantillas_consentimiento, consentimientos, historia_clinica_accesos, odontograma_registros, tratamiento_items, tratamientos, pagos, caja_sesiones, caja_movimientos, gastos, comprobante_correlativos FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON TABLE notas_evolucion, antecedentes_historial, plantillas_consentimiento, consentimientos, historia_clinica_accesos, odontograma_registros, tratamiento_items, tratamientos, pagos, caja_sesiones, caja_movimientos, gastos, comprobante_correlativos FROM authenticated';
    END IF;
END $$;
