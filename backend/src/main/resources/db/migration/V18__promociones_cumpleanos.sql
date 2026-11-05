-- ============================================================================
-- V18: Promociones de Cumpleaños Automatizadas (Backend v2 - Sección 6)
-- Configuración por sede, registro de promociones emitidas, uso auditado y reportes
-- ============================================================================

CREATE TABLE IF NOT EXISTS configuracion_promociones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sede_id VARCHAR(50) NOT NULL REFERENCES sedes(id),
    tipo VARCHAR(30) NOT NULL DEFAULT 'PORCENTAJE',
    valor NUMERIC(10,2) NOT NULL DEFAULT 20.00,
    descripcion_beneficio VARCHAR(255) NOT NULL DEFAULT '20% de descuento en Profilaxis Dental',
    plantilla_mensaje TEXT NOT NULL DEFAULT '¡Feliz cumpleaños {paciente}! 🎂 De parte de todo el equipo de {sede} le deseamos un día extraordinario. Tiene activo un beneficio especial de {beneficio} vigente durante los próximos {dias} días. ¡Venga a sonreír con nosotros!',
    dias_vigencia INTEGER NOT NULL DEFAULT 7,
    activa BOOLEAN NOT NULL DEFAULT true,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ,
    CONSTRAINT uk_configuracion_promociones_sede UNIQUE (sede_id)
);

-- Valores iniciales para las tres sedes oficiales
INSERT INTO configuracion_promociones (sede_id, tipo, valor, descripcion_beneficio, dias_vigencia)
VALUES 
    ('sede-chilca-1', 'PORCENTAJE', 20.00, '20% de descuento en Profilaxis Dental', 7),
    ('sede-eltambo', 'PORCENTAJE', 20.00, '20% de descuento en Profilaxis Dental', 7),
    ('sede-villamax-chilca', 'PORCENTAJE', 15.00, '15% de descuento en Tomografía 3D Diagnóstica', 7)
ON CONFLICT (sede_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS promociones_paciente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id UUID NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    sede_id VARCHAR(50) NOT NULL REFERENCES sedes(id),
    anio INTEGER NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    valor NUMERIC(10,2),
    beneficio VARCHAR(255) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    mensaje_personalizado TEXT,
    enviado_whatsapp BOOLEAN NOT NULL DEFAULT false,
    enviado_en TIMESTAMPTZ,
    enviado_por VARCHAR(100),
    usado BOOLEAN NOT NULL DEFAULT false,
    usado_en TIMESTAMPTZ,
    cita_id UUID REFERENCES citas(id),
    usado_por VARCHAR(100),
    notas_uso VARCHAR(500),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_promocion_paciente_anio UNIQUE (paciente_id, anio)
);

CREATE INDEX IF NOT EXISTS idx_promociones_paciente_vigente 
    ON promociones_paciente (paciente_id, usado, fecha_vencimiento);

CREATE INDEX IF NOT EXISTS idx_promociones_sede_emision 
    ON promociones_paciente (sede_id, fecha_emision);
