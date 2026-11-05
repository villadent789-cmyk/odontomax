-- V51: Doctor oficial de Villamax Chilca y horarios base de Huancayo.
-- Solo agrega lo que falta: nunca pisa nombres, sillones ni horarios que el administrador ya haya cambiado.

INSERT INTO especialistas (id, usuario_id, nombre, especialidad, cop, email, telefono, activo) VALUES
('10000000-0000-0000-0000-000000000007', NULL, 'Doctor Villamax', 'Radiología Bucal & Maxilofacial', 'COP-00007', 'doctor.villamax@odontomax.pe', '960 112 257', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO especialista_sedes (especialista_id, sede_id, sillon_numero) VALUES
('10000000-0000-0000-0000-000000000007', 'sede-villamax-chilca', 1)
ON CONFLICT (especialista_id, sede_id) DO NOTHING;

-- Villamax: lunes a sábado 08:00 - 19:30
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT '10000000-0000-0000-0000-000000000007', 'sede-villamax-chilca', d, TIME '08:00', TIME '19:30', true
FROM generate_series(1, 6) AS d
ON CONFLICT (especialista_id, sede_id, dia_semana) DO NOTHING;

-- Huancayo Centro: lunes a sábado 09:00 - 20:00 para los especialistas que aún no tengan horario ese día
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT es.especialista_id, es.sede_id, d.dia, TIME '09:00', TIME '20:00', true
FROM especialista_sedes es
JOIN especialistas e ON e.id = es.especialista_id AND e.activo
CROSS JOIN generate_series(1, 6) AS d(dia)
WHERE es.sede_id = 'sede-eltambo'
ON CONFLICT (especialista_id, sede_id, dia_semana) DO NOTHING;
