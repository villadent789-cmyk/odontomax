-- =============================================================================
-- V20 · Módulo de Laboratorio Dental & Prótesis
-- Gestión de proveedores/laboratorios externos, órdenes de prótesis dental,
-- piezas, colorimetría VITA, trazabilidad de estados clínicos y costos.
-- =============================================================================

-- 1. Catálogo de Laboratorios Dentales Externos
CREATE TABLE IF NOT EXISTS laboratorios_externos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(150) NOT NULL,
    contacto VARCHAR(120),
    telefono VARCHAR(30),
    email VARCHAR(120),
    direccion VARCHAR(255),
    tiempo_entrega_promedio_dias INTEGER DEFAULT 5,
    activo BOOLEAN NOT NULL DEFAULT true,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_laboratorios_activo ON laboratorios_externos(activo);

-- 2. Órdenes de Trabajo de Prótesis y Laboratorio Dental
CREATE TABLE IF NOT EXISTS ordenes_laboratorio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    paciente_id UUID NOT NULL REFERENCES pacientes(id),
    especialista_id UUID NOT NULL REFERENCES especialistas(id),
    sede_id VARCHAR(50) NOT NULL REFERENCES sedes(id),
    laboratorio_id UUID NOT NULL REFERENCES laboratorios_externos(id),
    tipo_trabajo VARCHAR(60) NOT NULL,
    color_tono VARCHAR(30),
    piezas_dentales VARCHAR(100),
    estado VARCHAR(30) NOT NULL DEFAULT 'SOLICITADO',
    fecha_solicitud DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_envio_laboratorio TIMESTAMPTZ,
    fecha_estimada_entrega DATE,
    fecha_recepcion_clinica TIMESTAMPTZ,
    fecha_instalacion TIMESTAMPTZ,
    costo_laboratorio NUMERIC(10,2) CHECK (costo_laboratorio >= 0),
    precio_paciente NUMERIC(10,2) CHECK (precio_paciente >= 0),
    guia_remision VARCHAR(60),
    indicaciones_tecnicas TEXT,
    observaciones TEXT,
    registrado_por VARCHAR(120),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_orden_laboratorio_estado CHECK (
        estado IN ('SOLICITADO', 'EN_LABORATORIO', 'EN_PRUEBA_CLINICA', 'AJUSTE_REQUERIDO', 'RECIBIDO_CLINICA', 'INSTALADO_ENTREGADO', 'CANCELADO')
    )
);

CREATE INDEX IF NOT EXISTS idx_ordenes_lab_paciente ON ordenes_laboratorio(paciente_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_lab_especialista ON ordenes_laboratorio(especialista_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_lab_sede ON ordenes_laboratorio(sede_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_lab_estado ON ordenes_laboratorio(estado);
CREATE INDEX IF NOT EXISTS idx_ordenes_lab_fecha_solicitud ON ordenes_laboratorio(fecha_solicitud DESC);

-- 3. Historial de Transiciones de Estado
CREATE TABLE IF NOT EXISTS ordenes_laboratorio_historial (
    id BIGSERIAL PRIMARY KEY,
    orden_id UUID NOT NULL REFERENCES ordenes_laboratorio(id) ON DELETE CASCADE,
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30) NOT NULL,
    usuario_nombre VARCHAR(120) NOT NULL,
    usuario_rol VARCHAR(50) NOT NULL,
    motivo VARCHAR(255),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ordenes_lab_historial_orden ON ordenes_laboratorio_historial(orden_id);

-- 4. Secuencia para correlativo amigable LAB-YYYY-XXXX
CREATE SEQUENCE IF NOT EXISTS seq_orden_laboratorio_correlativo START WITH 1 INCREMENT BY 1;

-- 5. Seed inicial de laboratorios dentales representativos
INSERT INTO laboratorios_externos (id, nombre, contacto, telefono, email, direccion, tiempo_entrega_promedio_dias, activo) VALUES
('50000000-0000-0000-0000-000000000001', 'Lab Dental Real Huancayo', 'Téc. Juan Pérez Huamán', '964 112 233', 'labreal@odontomax.pe', 'Jr. Puno 340, Huancayo', 4, true),
('50000000-0000-0000-0000-000000000002', 'CeramLab 3D Especialistas', 'Dra. Carmen Salazar', '954 889 900', 'ceramlab3d@odontomax.pe', 'Av. Giráldez 512, Huancayo', 5, true),
('50000000-0000-0000-0000-000000000003', 'Prótesis & Retenedores del Centro', 'Téc. Manuel Castro', '962 334 455', 'protesiscentro@odontomax.pe', 'Calle Real 820, Chilca', 3, true)
ON CONFLICT (id) DO NOTHING;
