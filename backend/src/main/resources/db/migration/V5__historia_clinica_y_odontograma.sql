-- =============================================================================
-- V5: Historia Clínica, Odontograma Persistente, Evolución y Consentimientos
-- =============================================================================

-- 1. Tabla de Notas de Evolución Clínica (Inmutables una vez firmadas)
CREATE TABLE IF NOT EXISTS notas_evolucion (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id             uuid NOT NULL REFERENCES pacientes(id),
    cita_id                 uuid REFERENCES citas(id),
    autor_id                uuid NOT NULL REFERENCES usuarios(id),
    autor_nombre            text NOT NULL,
    autor_rol               text NOT NULL,
    tipo_nota               text NOT NULL DEFAULT 'SOAP' CHECK (tipo_nota IN ('SOAP', 'TEXTO_LIBRE')),
    subjetivo               text,
    objetivo                text,
    analisis                text,
    plan                    text,
    contenido               text,
    nota_referencia_id      uuid REFERENCES notas_evolucion(id),
    motivo_correccion       text,
    firmado                 boolean NOT NULL DEFAULT true,
    creado_en               timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notas_evolucion_paciente ON notas_evolucion (paciente_id, creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_notas_evolucion_cita ON notas_evolucion (cita_id);

-- 2. Historial de Antecedentes Médicos y Alergias del Paciente
CREATE TABLE IF NOT EXISTS antecedentes_historial (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id             uuid NOT NULL REFERENCES pacientes(id),
    alergias                text,
    antecedentes_medicos    text,
    es_gestante             boolean NOT NULL DEFAULT false,
    semanas_gestacion       integer CHECK (semanas_gestacion IS NULL OR semanas_gestacion BETWEEN 1 AND 45),
    motivo_cambio           text NOT NULL,
    modificado_por          uuid REFERENCES usuarios(id),
    modificado_por_nombre   text,
    creado_en               timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_antecedentes_paciente ON antecedentes_historial (paciente_id, creado_en DESC);

-- 3. Plantillas de Consentimientos Informados
CREATE TABLE IF NOT EXISTS plantillas_consentimiento (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                  varchar(50) UNIQUE NOT NULL,
    titulo                  text NOT NULL,
    tipo                    text NOT NULL CHECK (tipo IN ('TRATAMIENTO_DATOS','INFORMADO_PROCEDIMIENTO','USO_IMAGEN')),
    contenido               text NOT NULL,
    version                 integer NOT NULL DEFAULT 1,
    activo                  boolean NOT NULL DEFAULT true,
    creado_en               timestamptz NOT NULL DEFAULT now()
);

-- Extender tabla consentimientos existente para soportar firma en tablet y PDF R2
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS plantilla_id uuid REFERENCES plantillas_consentimiento(id);
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS titulo text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS contenido_aceptado text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS firma_imagen_key text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS pdf_key text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS nombre_firmante text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS documento_firmante text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS parentesco_firmante text;
ALTER TABLE consentimientos ADD COLUMN IF NOT EXISTS ip_firmante text;

-- Sembrar plantillas por defecto si no existen
INSERT INTO plantillas_consentimiento (codigo, titulo, tipo, contenido, version, activo)
VALUES 
(
    'CONS-DATOS-V1',
    'Consentimiento para Tratamiento de Datos Personales (Ley 29733)',
    'TRATAMIENTO_DATOS',
    'Autorizo expresamente a Clínica Odontológica OdontoMax el tratamiento de mis datos personales y de salud exclusivamente para fines de diagnóstico, historia clínica, recordatorios de citas y gestión de tratamientos dentales conforme a la Ley N° 29733.',
    1,
    true
),
(
    'CONS-PROC-V1',
    'Consentimiento Informado para Procedimientos Odontológicos Generales',
    'INFORMADO_PROCEDIMIENTO',
    'He sido debidamente informado(a) por el profesional odontólogo sobre el diagnóstico, la naturaleza del procedimiento a realizar, los beneficios esperados, los riesgos inherentes y los cuidados postoperatorios que debo seguir. Otorgo mi conformidad voluntaria para su realización.',
    1,
    true
),
(
    'CONS-IMAGEN-V1',
    'Consentimiento para Uso de Imágenes Clínicas y Radiográficas',
    'USO_IMAGEN',
    'Autorizo la toma y archivo de fotografías clínicas y radiografías dentales para mi expediente de salud, planificación diagnóstica y comparación de evolución del tratamiento.',
    1,
    true
)
ON CONFLICT (codigo) DO NOTHING;

-- 4. Registro de Auditoría de Lectura de Historia Clínica (Item 1.5)
CREATE TABLE IF NOT EXISTS historia_clinica_accesos (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id             uuid NOT NULL REFERENCES pacientes(id),
    usuario_id             uuid NOT NULL REFERENCES usuarios(id),
    usuario_nombre          text NOT NULL,
    usuario_email           text NOT NULL,
    usuario_rol             text NOT NULL,
    sede_id                 text NOT NULL,
    ip                      text NOT NULL,
    accedido_en             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_hc_accesos_paciente ON historia_clinica_accesos (paciente_id, accedido_en DESC);
CREATE INDEX IF NOT EXISTS idx_hc_accesos_fecha ON historia_clinica_accesos (accedido_en DESC);
