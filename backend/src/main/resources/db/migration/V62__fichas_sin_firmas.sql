-- La firma del paciente (o del tutor) se recoge en el consentimiento informado, no en la ficha clínica del tratamiento.
-- La ficha de odontopediatría solo registra quién acompaña al menor.
UPDATE plantillas_tratamiento
   SET esquema = replace(esquema::text, 'Tutor que acompaña y firma', 'Tutor o apoderado que acompaña')::jsonb,
       version = version + 1
 WHERE codigo = 'ODONTOPEDIATRIA' AND esquema::text LIKE '%Tutor que acompaña y firma%';
