-- =============================================================================
-- V15 · Unidades de Negocio (Villadent & Villamax) y Sedes Oficiales v2
-- Conforme a docs/especificaciones-v2.md:
-- 1. Organización: dos unidades de negocio (VILLADENT y VILLAMAX).
-- 2. Sedes oficiales: Villadent Chilca, Villadent Huancayo Centro, Villamax Chilca.
-- =============================================================================

-- 1. Agregar columna unidad_de_negocio a la tabla sedes
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS unidad_de_negocio text NOT NULL DEFAULT 'VILLADENT';
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS ruc text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_sedes_unidad_de_negocio'
    ) THEN
        ALTER TABLE sedes ADD CONSTRAINT chk_sedes_unidad_de_negocio
            CHECK (unidad_de_negocio IN ('VILLADENT', 'VILLAMAX'));
    END IF;
END $$;

-- 2. Actualizar nombres oficiales de sedes existentes
UPDATE sedes
SET nombre = 'Villadent Chilca',
    unidad_de_negocio = 'VILLADENT',
    color = '#0F5C5A',
    color_secundario = '#FF7A59'
WHERE id = 'sede-chilca-1';

UPDATE sedes
SET nombre = 'Villadent Huancayo Centro',
    direccion = 'Calle Real 450 (Centro de Huancayo)',
    ciudad = 'Huancayo - Centro',
    unidad_de_negocio = 'VILLADENT',
    color = '#0F5C5A',
    color_secundario = '#FF7A59'
WHERE id = 'sede-eltambo';

-- 3. Insertar / registrar sede oficial Villamax Chilca
INSERT INTO sedes (id, nombre, direccion, ciudad, telefono, whatsapp, email, horario_texto,
                   color, color_secundario, sillones, descripcion, activa, unidad_de_negocio)
VALUES ('sede-villamax-chilca', 'Villamax Chilca', 'Jr. Ricardo Palma 411 (a 3 cuadras arriba del mercado Chilca)', 'Huancayo - Chilca',
        '960 112 257', '51960112257', 'contacto@villamax.pe', 'Lun - Sáb: 8:00 a.m. - 7:30 p.m.',
        '#2B3A8C', '#22B8CF', 1, 'Centro radiológico de alta precisión, tomografía 3D Cone Beam y diagnóstico maxilofacial.',
        true, 'VILLAMAX')
ON CONFLICT (id) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    direccion = EXCLUDED.direccion,
    color = EXCLUDED.color,
    color_secundario = EXCLUDED.color_secundario,
    unidad_de_negocio = EXCLUDED.unidad_de_negocio,
    activa = true;

-- Horarios de atención para Villamax Chilca (lunes a sábado 8:00 a 19:30)
INSERT INTO sede_horarios (sede_id, dia_semana, hora_apertura, hora_cierre)
SELECT 'sede-villamax-chilca', d, TIME '08:00', TIME '19:30'
FROM generate_series(1, 6) AS d
ON CONFLICT (sede_id, dia_semana) DO NOTHING;
