-- =============================================================================
-- V21 · Doctores Derivadores y Comisiones Radiológicas (Villamax)
-- Soporte para:
-- 1. Registro de médicos / odontólogos derivadores externos y clínicas asociadas.
-- 2. Trazabilidad de comisiones por derivación de radiografías / tomografías 3D.
-- 3. Liquidación con métodos de pago (Yape, Plin, Transferencia, Efectivo).
-- =============================================================================

CREATE TABLE IF NOT EXISTS doctores_derivadores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(150) NOT NULL,
    cop VARCHAR(20),
    clinica_origen VARCHAR(150),
    telefono VARCHAR(30),
    banco VARCHAR(50),
    numero_cuenta_o_yape VARCHAR(50),
    porcentaje_o_tarifa_fija NUMERIC(10, 2) DEFAULT 10.00,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    notas TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS comisiones_radiologicas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_derivador_id UUID NOT NULL REFERENCES doctores_derivadores(id) ON DELETE RESTRICT,
    paciente_nombre VARCHAR(150) NOT NULL,
    paciente_id UUID,
    tipo_estudio VARCHAR(80) NOT NULL,
    precio_cobrado NUMERIC(10, 2) NOT NULL,
    monto_comision NUMERIC(10, 2) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_emision TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_liquidacion TIMESTAMPTZ,
    metodo_pago VARCHAR(30),
    codigo_operacion VARCHAR(60),
    nota TEXT,
    sede_id VARCHAR(50) DEFAULT 'sede-villamax-chilca',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    registrado_por VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_comisiones_doctor ON comisiones_radiologicas(doctor_derivador_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_estado ON comisiones_radiologicas(estado);
CREATE INDEX IF NOT EXISTS idx_comisiones_fecha ON comisiones_radiologicas(fecha_emision);

-- Seed de doctores derivadores de referencia en el Valle del Mantaro
INSERT INTO doctores_derivadores (id, nombre, cop, clinica_origen, telefono, banco, numero_cuenta_o_yape, porcentaje_o_tarifa_fija, activo)
VALUES
    ('d0000001-0000-4000-8000-000000000001', 'Dr. Carlos Palomino Quispe', 'COP 28410', 'Clínica Dental Sonrisas (El Tambo)', '964123456', 'BCP / Yape', '964123456', 10.00, true),
    ('d0000002-0000-4000-8000-000000000002', 'Dra. Patricia Huamán Ramos', 'COP 31055', 'Consultorio Dental Art (Centro)', '954789123', 'Interbank / Plin', '954789123', 15.00, true),
    ('d0000003-0000-4000-8000-000000000003', 'Dr. Walter Mendoza Chanca', 'COP 24902', 'Centro Odontológico Huancayo', '987654321', 'BBVA', '0011-0234-0200987654', 10.00, true)
ON CONFLICT (id) DO NOTHING;

-- Seed de comisiones de ejemplo para trazabilidad inicial
INSERT INTO comisiones_radiologicas (id, doctor_derivador_id, paciente_nombre, tipo_estudio, precio_cobrado, monto_comision, estado, fecha_emision, sede_id, registrado_por)
VALUES
    ('c0000001-0000-4000-8000-000000000001', 'd0000001-0000-4000-8000-000000000001', 'Luciana Meza Cárdenas', 'PANORAMICA', 50.00, 10.00, 'PENDIENTE', NOW() - INTERVAL '2 days', 'sede-villamax-chilca', 'Recepcionista Chilca'),
    ('c0000002-0000-4000-8000-000000000002', 'd0000002-0000-4000-8000-000000000002', 'Gonzalo Astuvilca Ramos', 'TOMOGRAFIA_3D_CONE_BEAM', 180.00, 35.00, 'PENDIENTE', NOW() - INTERVAL '1 day', 'sede-villamax-chilca', 'Recepcionista Chilca'),
    ('c0000003-0000-4000-8000-000000000003', 'd0000001-0000-4000-8000-000000000001', 'Renzo Huaylinos Ramos', 'CEFALOMETRICA', 60.00, 12.00, 'LIQUIDADO', NOW() - INTERVAL '5 days', 'sede-villamax-chilca', 'Recepcionista Chilca')
ON CONFLICT (id) DO NOTHING;
