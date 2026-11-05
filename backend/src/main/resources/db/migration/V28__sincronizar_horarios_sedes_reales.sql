-- =============================================================================
-- V28: Sincronizar horarios de doctores y migrar referencias de sede-chilca-2
-- =============================================================================

-- 1. Reasignar datos huérfanos de la sede obsoleta sede-chilca-2 hacia sede-chilca-1
UPDATE usuarios SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE citas SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE pacientes SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
DELETE FROM especialista_horarios h
WHERE h.sede_id = 'sede-chilca-2'
  AND EXISTS (SELECT 1 FROM especialista_horarios o
              WHERE o.sede_id = 'sede-chilca-1' AND o.especialista_id = h.especialista_id AND o.dia_semana = h.dia_semana);
UPDATE especialista_horarios SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
DELETE FROM inventario_stock WHERE sede_id = 'sede-chilca-2';

-- 2. Asegurar que especialista_sedes tenga vinculadas las sedes oficiales
INSERT INTO especialista_sedes (especialista_id, sede_id)
SELECT e.id, s.id
FROM especialistas e, sedes s
WHERE e.id = '10000000-0000-0000-0000-000000000001' AND s.id IN ('sede-chilca-1', 'sede-eltambo')
ON CONFLICT DO NOTHING;

INSERT INTO especialista_sedes (especialista_id, sede_id)
SELECT e.id, s.id
FROM especialistas e, sedes s
WHERE e.id = '10000000-0000-0000-0000-000000000002' AND s.id IN ('sede-chilca-1', 'sede-eltambo')
ON CONFLICT DO NOTHING;

INSERT INTO especialista_sedes (especialista_id, sede_id)
SELECT e.id, s.id
FROM especialistas e, sedes s
WHERE e.id = '10000000-0000-0000-0000-000000000003' AND s.id IN ('sede-eltambo', 'sede-villamax-chilca')
ON CONFLICT DO NOTHING;

-- 3. Horario Villadent: lunes a domingo de 09:00 a 20:00 en cada sede asignada (misma regla que V24, ahora con las asignaciones de la sección 2)
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT es.especialista_id, es.sede_id, d.dia, TIME '09:00', TIME '20:00', true
FROM especialista_sedes es
JOIN especialistas e ON e.id = es.especialista_id AND e.activo
JOIN sedes s ON s.id = es.sede_id AND s.activa AND s.unidad_de_negocio = 'VILLADENT'
CROSS JOIN generate_series(1, 7) AS d(dia)
ON CONFLICT (especialista_id, sede_id, dia_semana)
DO UPDATE SET hora_inicio = EXCLUDED.hora_inicio, hora_fin = EXCLUDED.hora_fin, activo = true;
