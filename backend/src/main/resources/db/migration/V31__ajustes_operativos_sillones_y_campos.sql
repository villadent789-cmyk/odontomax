-- =============================================================================
-- V31 · Ajustes Operativos: Capacidad de Sedes, Lotes de Inventario y Laboratorio
-- =============================================================================

-- 1. Capacidad oficial de sillones dentales por sede
-- Sede Chilca (Villadent Chilca) = 4 sillones
UPDATE sedes SET sillones = 4 WHERE id = 'sede-chilca-1';
-- Sede Huancayo (Villadent Huancayo Centro) = 2 sillones
UPDATE sedes SET sillones = 2 WHERE id = 'sede-eltambo';

-- 2. Trazabilidad de inventario: fecha de entrega / recepción de productos y lotes
ALTER TABLE inventario_movimientos ADD COLUMN IF NOT EXISTS fecha_entrega date;

-- 3. Ficha de laboratorio dental: material y prótesis provisional
ALTER TABLE ordenes_laboratorio ADD COLUMN IF NOT EXISTS material varchar(100);
ALTER TABLE ordenes_laboratorio ADD COLUMN IF NOT EXISTS provisional boolean NOT NULL DEFAULT false;
