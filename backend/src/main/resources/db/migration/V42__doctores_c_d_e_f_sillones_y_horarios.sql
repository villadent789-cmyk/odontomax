-- V42: Seis doctores oficiales con sillón fijo y horario de lunes a domingo
-- Villadent Chilca: Doctor A (sillón 1), Doctor B (sillón 2), Doctor C (sillón 3), Doctor D (sillón 4)
-- Villadent Huancayo Centro: Doctor E (sillón 1), Doctor F (sillón 2)

-- 1. Desvincular Doctor A y Doctor B de Huancayo Centro (sede-eltambo), ya que ahora atienden exclusivamente en Chilca
DELETE FROM especialista_horarios
WHERE especialista_id IN ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
  AND sede_id = 'sede-eltambo';

DELETE FROM especialista_sedes
WHERE especialista_id IN ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
  AND sede_id = 'sede-eltambo';

-- 2. Asegurar sillones fijos de Doctor A y Doctor B en Villadent Chilca
UPDATE especialista_sedes
SET sillon_numero = 1
WHERE especialista_id = '10000000-0000-0000-0000-000000000001' AND sede_id = 'sede-chilca-1';

UPDATE especialista_sedes
SET sillon_numero = 2
WHERE especialista_id = '10000000-0000-0000-0000-000000000002' AND sede_id = 'sede-chilca-1';

-- 3. Crear Doctores C, D, E y F si no existen
INSERT INTO especialistas (id, usuario_id, nombre, especialidad, cop, email, telefono, activo) VALUES
('10000000-0000-0000-0000-000000000003', NULL, 'Doctor C', 'Endodoncia & Rehabilitación Oral', 'COP-00003', 'doctor.c@odontomax.pe', '943 672 062', true),
('10000000-0000-0000-0000-000000000004', NULL, 'Doctor D', 'Periodoncia & Implantología', 'COP-00004', 'doctor.d@odontomax.pe', '943 672 062', true),
('10000000-0000-0000-0000-000000000005', NULL, 'Doctor E', 'Odontología General & Estética', 'COP-00005', 'doctor.e@odontomax.pe', '943 672 062', true),
('10000000-0000-0000-0000-000000000006', NULL, 'Doctor F', 'Cirugía Bucal & Maxilofacial', 'COP-00006', 'doctor.f@odontomax.pe', '943 672 062', true)
ON CONFLICT (id) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    especialidad = EXCLUDED.especialidad,
    cop = EXCLUDED.cop,
    email = EXCLUDED.email,
    telefono = EXCLUDED.telefono,
    activo = EXCLUDED.activo;

-- 4. Asignar sedes y sillones fijos (Chilca: sillones 3 y 4; Huancayo Centro: sillones 1 y 2)
INSERT INTO especialista_sedes (especialista_id, sede_id, sillon_numero) VALUES
('10000000-0000-0000-0000-000000000003', 'sede-chilca-1', 3),
('10000000-0000-0000-0000-000000000004', 'sede-chilca-1', 4),
('10000000-0000-0000-0000-000000000005', 'sede-eltambo', 1),
('10000000-0000-0000-0000-000000000006', 'sede-eltambo', 2)
ON CONFLICT (especialista_id, sede_id) DO UPDATE
SET sillon_numero = EXCLUDED.sillon_numero;

-- 5. Horario oficial de atención (09:00 - 20:00, lunes a domingo) para las sedes Villadent
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT es.especialista_id, es.sede_id, d.dia, TIME '09:00', TIME '20:00', true
FROM especialista_sedes es
JOIN especialistas e ON e.id = es.especialista_id AND e.activo
JOIN sedes s ON s.id = es.sede_id AND s.activa AND s.unidad_de_negocio = 'VILLADENT'
CROSS JOIN generate_series(1, 7) AS d(dia)
ON CONFLICT (especialista_id, sede_id, dia_semana)
DO UPDATE SET hora_inicio = EXCLUDED.hora_inicio, hora_fin = EXCLUDED.hora_fin, activo = true;
