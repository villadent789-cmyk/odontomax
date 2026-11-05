-- =============================================================================
-- SOLO DESARROLLO LOCAL · Datos de ejemplo para el perfil "local"
-- NO es una migración Flyway: lo ejecuta LocalSeedRunner (@Profile("local")) en cada arranque.
-- Idempotente: identificadores fijos + ON CONFLICT DO NOTHING (no duplica ni pisa datos editados).
-- Sedes, servicios, especialistas y sus horarios ya los siembran V2, V8, V12 y V13.
-- Los usuarios de prueba se crean desde LocalSeedRunner (necesitan hash BCrypt).
-- =============================================================================

-- Pacientes de ejemplo (datos ficticios)
INSERT INTO pacientes (id, tipo_documento, dni, nombres, apellidos, fecha_nacimiento, sexo, telefono, email,
                       direccion, sede_id, fuente_captacion, alergias, consentimiento_datos_en) VALUES
('30000000-0000-0000-0000-000000000001', 'DNI', '70000001', 'Lucía', 'Quispe Rojas', DATE '1990-04-12', 'F',
 '900000001', 'lucia.quispe@example.test', 'Jr. Ficticio 101, Chilca', 'sede-chilca-1', 'FACEBOOK', NULL, now()),
('30000000-0000-0000-0000-000000000002', 'DNI', '70000002', 'Carlos', 'Huamán Torres', DATE '1985-09-30', 'M',
 '900000002', 'carlos.huaman@example.test', 'Av. Ficticia 202, El Tambo', 'sede-chilca-1', 'RECOMENDACION', 'Penicilina', now()),
('30000000-0000-0000-0000-000000000003', 'DNI', '70000003', 'Mateo', 'Ramos Vilca', DATE '2015-02-18', 'M',
 '900000003', NULL, 'Psje. Ficticio 303, Chilca', 'sede-chilca-1', 'WEB', NULL, now()),
('30000000-0000-0000-0000-000000000004', 'DNI', '70000004', 'Rosa', 'Mendoza Salazar', DATE '1972-11-05', 'F',
 '900000004', 'rosa.mendoza@example.test', 'Calle Ficticia 404, El Tambo', 'sede-eltambo', 'WALK_IN', NULL, now())
ON CONFLICT DO NOTHING;

-- Citas de ejemplo relativas a la fecha del primer arranque (hoy y los próximos días)
INSERT INTO citas (id, paciente_id, paciente_nombre, paciente_telefono, doctor_id, doctor_nombre, sede_id,
                   sillon_numero, servicio_id, motivo_consulta, fecha, hora, duracion_minutos, estado, origen) VALUES
('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'Lucía Quispe Rojas', '900000001',
 '10000000-0000-0000-0000-000000000001', 'Doctor A', 'sede-chilca-1', 1, 'srv-eval-odon',
 'Evaluación inicial (dato de prueba)', CURRENT_DATE, TIME '10:00', 30, 'CONFIRMADA', 'INTERNO'),
('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', 'Carlos Huamán Torres', '900000002',
 '10000000-0000-0000-0000-000000000001', 'Doctor A', 'sede-chilca-1', 2, 'srv-profilaxis',
 'Limpieza dental (dato de prueba)', CURRENT_DATE, TIME '11:00', 45, 'PROGRAMADA', 'TELEFONO'),
('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', 'Mateo Ramos Vilca', '900000003',
 '10000000-0000-0000-0000-000000000002', 'Doctor B', 'sede-chilca-1', 1, 'srv-sellantes',
 'Control odontopediátrico (dato de prueba)', CURRENT_DATE + 1, TIME '09:30', 30, 'PROGRAMADA', 'WHATSAPP'),
('40000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000004', 'Rosa Mendoza Salazar', '900000004',
 '10000000-0000-0000-0000-000000000005', 'Doctor E', 'sede-eltambo', 1, 'srv-extracciones',
 'Extracción simple (dato de prueba)', CURRENT_DATE + 2, TIME '15:00', 60, 'PROGRAMADA', 'INTERNO'),
('40000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000001', 'Lucía Quispe Rojas', '900000001',
 '10000000-0000-0000-0000-000000000001', 'Doctor A', 'sede-chilca-1', NULL, 'srv-curaciones',
 'Solicitud web (dato de prueba)', CURRENT_DATE + 3, TIME '16:00', 30, 'PENDIENTE_WEB', 'WEB')
ON CONFLICT DO NOTHING;

-- Ticket para la cita web de ejemplo (consulta pública por código)
UPDATE citas SET codigo_ticket = 'RV-LOCAL001'
WHERE id = '40000000-0000-0000-0000-000000000005' AND codigo_ticket IS NULL;
