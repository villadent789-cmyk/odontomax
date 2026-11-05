-- =============================================================================
-- V29: Limpiar sede obsoleta sede-chilca-2 de especialistas_sedes y especialista_horarios
-- Desvincular definitivamente a los doctores de la sede histórica desactivada
-- =============================================================================

DELETE FROM especialista_sedes WHERE sede_id = 'sede-chilca-2';
DELETE FROM especialista_horarios WHERE sede_id = 'sede-chilca-2';
DELETE FROM inventario_stock WHERE sede_id = 'sede-chilca-2';
UPDATE sedes SET activa = false WHERE id = 'sede-chilca-2';
