-- Villadent atiende de lunes a domingo, de 09:00 a 20:00, en cada sede asignada al doctor.
-- Corrige las bases que aplicaron la V28 antigua (turnos partidos de lunes a sábado). Es idempotente: en bases nuevas repite lo que ya hizo V28.
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT es.especialista_id, es.sede_id, d.dia, TIME '09:00', TIME '20:00', true
FROM especialista_sedes es
JOIN especialistas e ON e.id = es.especialista_id AND e.activo
JOIN sedes s ON s.id = es.sede_id AND s.activa AND s.unidad_de_negocio = 'VILLADENT'
CROSS JOIN generate_series(1, 7) AS d(dia)
ON CONFLICT (especialista_id, sede_id, dia_semana)
DO UPDATE SET hora_inicio = EXCLUDED.hora_inicio, hora_fin = EXCLUDED.hora_fin, activo = true;
