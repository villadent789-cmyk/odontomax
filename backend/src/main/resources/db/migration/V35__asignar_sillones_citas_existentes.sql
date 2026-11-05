UPDATE citas cita
SET sillon_numero = asignacion.sillon_numero
FROM especialista_sedes asignacion
WHERE cita.sillon_numero IS NULL
  AND cita.doctor_id = asignacion.especialista_id
  AND cita.sede_id = asignacion.sede_id
  AND asignacion.sillon_numero IS NOT NULL
  AND cita.estado NOT IN ('CANCELADA', 'NO_ASISTIO')
  AND NOT EXISTS (
      SELECT 1
      FROM citas ocupada
      WHERE ocupada.id <> cita.id
        AND ocupada.sede_id = cita.sede_id
        AND ocupada.sillon_numero = asignacion.sillon_numero
        AND ocupada.estado NOT IN ('CANCELADA', 'NO_ASISTIO')
        AND rango_cita(ocupada.fecha, ocupada.hora, ocupada.duracion_minutos)
            && rango_cita(cita.fecha, cita.hora, cita.duracion_minutos)
  )
  AND NOT EXISTS (
      SELECT 1
      FROM citas pendiente
      JOIN especialista_sedes otra_asignacion
        ON otra_asignacion.especialista_id = pendiente.doctor_id
       AND otra_asignacion.sede_id = pendiente.sede_id
       AND otra_asignacion.sillon_numero = asignacion.sillon_numero
      WHERE pendiente.id <> cita.id
        AND pendiente.sillon_numero IS NULL
        AND pendiente.sede_id = cita.sede_id
        AND pendiente.estado NOT IN ('CANCELADA', 'NO_ASISTIO')
        AND rango_cita(pendiente.fecha, pendiente.hora, pendiente.duracion_minutos)
            && rango_cita(cita.fecha, cita.hora, cita.duracion_minutos)
  );
