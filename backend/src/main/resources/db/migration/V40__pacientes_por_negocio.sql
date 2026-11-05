-- Villamax (centro radiologico) y Villadent (clinica) son negocios aislados: un mismo documento puede existir
-- en ambos como dos fichas distintas. La unidad de negocio del paciente se toma siempre de su sede.
ALTER TABLE pacientes ADD COLUMN IF NOT EXISTS unidad_de_negocio text NOT NULL DEFAULT 'VILLADENT';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pacientes'::regclass
          AND conname = 'pacientes_unidad_de_negocio_check'
    ) THEN
        ALTER TABLE pacientes
            ADD CONSTRAINT pacientes_unidad_de_negocio_check
            CHECK (unidad_de_negocio IN ('VILLADENT', 'VILLAMAX'));
    END IF;
END $$;

UPDATE pacientes p
SET unidad_de_negocio = s.unidad_de_negocio
FROM sedes s
WHERE s.id = p.sede_id
  AND p.unidad_de_negocio IS DISTINCT FROM s.unidad_de_negocio;

CREATE OR REPLACE FUNCTION trg_paciente_unidad_de_negocio() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    SELECT s.unidad_de_negocio INTO NEW.unidad_de_negocio FROM sedes s WHERE s.id = NEW.sede_id;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS pacientes_unidad_de_negocio ON pacientes;
CREATE TRIGGER pacientes_unidad_de_negocio
    BEFORE INSERT OR UPDATE OF sede_id ON pacientes
    FOR EACH ROW EXECUTE FUNCTION trg_paciente_unidad_de_negocio();

-- El documento ya no es unico en todo el grupo: es unico dentro de cada negocio.
DO $$
DECLARE
    restriccion record;
BEGIN
    FOR restriccion IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'pacientes'::regclass
          AND c.contype = 'u'
          AND pg_get_constraintdef(c.oid) ILIKE '%tipo_documento%dni%'
          AND pg_get_constraintdef(c.oid) NOT ILIKE '%unidad_de_negocio%'
    LOOP
        EXECUTE format('ALTER TABLE pacientes DROP CONSTRAINT %I', restriccion.conname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacientes_documento_por_negocio
    ON pacientes (tipo_documento, dni, unidad_de_negocio);
