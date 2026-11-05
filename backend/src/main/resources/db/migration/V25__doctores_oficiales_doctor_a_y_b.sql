-- V25: Doctores oficiales Doctor A y Doctor B únicamente

-- 1. Actualizar especialista 1 a Doctor A
UPDATE especialistas
SET nombre = 'Doctor A',
    especialidad = 'Ortodoncia & Ortopedia Maxilar',
    cop = 'COP-00001',
    email = 'doctor.a@odontomax.pe',
    telefono = '943 672 062',
    activo = true
WHERE id = '10000000-0000-0000-0000-000000000001';

-- 2. Actualizar especialista 2 a Doctor B
UPDATE especialistas
SET nombre = 'Doctor B',
    especialidad = 'Odontopediatría & Cirugía Bucal',
    cop = 'COP-00002',
    email = 'doctor.b@odontomax.pe',
    telefono = '960 112 257',
    activo = true
WHERE id = '10000000-0000-0000-0000-000000000002';

-- 3. Reasignar cualquier referencia del especialista 3 a Doctor A antes de borrar
UPDATE citas
SET doctor_id = '10000000-0000-0000-0000-000000000001',
    doctor_nombre = 'Doctor A'
WHERE doctor_id = '10000000-0000-0000-0000-000000000003';

UPDATE citas
SET doctor_nombre = 'Doctor A'
WHERE doctor_id = '10000000-0000-0000-0000-000000000001';

UPDATE citas
SET doctor_nombre = 'Doctor B'
WHERE doctor_id = '10000000-0000-0000-0000-000000000002';

UPDATE odontograma_registros
SET especialista_id = '10000000-0000-0000-0000-000000000001'
WHERE especialista_id = '10000000-0000-0000-0000-000000000003';

UPDATE tratamientos
SET especialista_id = '10000000-0000-0000-0000-000000000001'
WHERE especialista_id = '10000000-0000-0000-0000-000000000003';

UPDATE radiografias_estudios
SET doctor_id = '10000000-0000-0000-0000-000000000001'
WHERE doctor_id = '10000000-0000-0000-0000-000000000003';

UPDATE ordenes_laboratorio
SET especialista_id = '10000000-0000-0000-0000-000000000001'
WHERE especialista_id = '10000000-0000-0000-0000-000000000003';

-- 4. Eliminar especialista 3 para que SOLO existan Doctor A y Doctor B
DELETE FROM especialista_sedes WHERE especialista_id = '10000000-0000-0000-0000-000000000003';
DELETE FROM especialista_horarios WHERE especialista_id = '10000000-0000-0000-0000-000000000003';
DELETE FROM especialistas WHERE id = '10000000-0000-0000-0000-000000000003';

-- 5. Doctores derivadores (Villamax)
UPDATE doctores_derivadores
SET nombre = 'Doctor A'
WHERE id = 'd0000001-0000-4000-8000-000000000001';