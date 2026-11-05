-- =============================================================================
-- V27 · Actualización de sedes oficiales Parque de los Héroes y remoción de adelantos web
-- 1. Actualizar direcciones y datos de contacto de sedes oficiales en Huancayo / Chilca
-- 2. Desactivar sede histórica duplicada (sede-chilca-2)
-- 3. Fijar monto_adelanto_defecto = 0.00 en todas las sedes (flujo directo sin adelanto web)
-- =============================================================================

-- Desactivar sede antigua duplicada (Ricardo Palma)
UPDATE sedes
SET activa = false
WHERE id = 'sede-chilca-2';

-- Actualizar sede principal Villadent Chilca
UPDATE sedes
SET nombre = 'Villadent Chilca',
    direccion = 'Parque de los Héroes',
    ciudad = 'Huancayo - Chilca',
    telefono = '943 672 062',
    whatsapp = '51943672062',
    horario_texto = 'Lun - Sáb: 9:00 a.m. - 8:00 p.m.',
    descripcion = 'Chilca, Parque de los Héroes (Local Chilca)',
    unidad_de_negocio = 'VILLADENT',
    monto_adelanto_defecto = 0.00,
    activa = true
WHERE id = 'sede-chilca-1';

-- Actualizar sede Villadent Huancayo Centro
UPDATE sedes
SET nombre = 'Villadent Huancayo Centro',
    direccion = 'Calle Real 450 (Centro de Huancayo)',
    ciudad = 'Huancayo - Centro',
    telefono = '943 672 062',
    whatsapp = '51943672062',
    horario_texto = 'Lun - Sáb: 9:00 a.m. - 8:00 p.m.',
    descripcion = 'Centro de Huancayo, a pocos metros de la Plaza Constitución',
    unidad_de_negocio = 'VILLADENT',
    monto_adelanto_defecto = 0.00,
    activa = true
WHERE id = 'sede-eltambo';

-- Actualizar sede Villamax Chilca (Centro Radiológico)
UPDATE sedes
SET nombre = 'Villamax Chilca',
    direccion = 'Parque de los Héroes',
    ciudad = 'Huancayo - Chilca',
    telefono = '960 112 257',
    whatsapp = '51960112257',
    horario_texto = 'Lun - Sáb: 8:00 a.m. - 7:30 p.m.',
    descripcion = 'Chilca, Parque de los Héroes (Centro Radiológico)',
    unidad_de_negocio = 'VILLAMAX',
    monto_adelanto_defecto = 0.00,
    activa = true
WHERE id = 'sede-villamax-chilca';

-- Garantizar que ninguna sede exija adelanto web obligatorio
UPDATE sedes
SET monto_adelanto_defecto = 0.00;
