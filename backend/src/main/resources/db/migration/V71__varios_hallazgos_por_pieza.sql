-- Odontograma con varios hallazgos por pieza (la norma pide anotar más de uno en la misma pieza: una caries en una cara y
-- una corona en mal estado, por ejemplo). Cada hallazgo (pieza + tipo) tiene su propia historia: el registro más reciente
-- manda, y quitarlo es registrar ese mismo hallazgo como inactivo. Un registro «sano» limpia la pieza entera.
ALTER TABLE odontograma_registros ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;

-- Hasta ahora cada pieza tenía un solo hallazgo, el último registro. Lo anterior queda inactivo para que el estado actual
-- de los pacientes existentes sea exactamente el mismo.
UPDATE odontograma_registros o SET activo = false
WHERE o.id NOT IN (
    SELECT DISTINCT ON (paciente_id, pieza_fdi) id
    FROM odontograma_registros
    ORDER BY paciente_id, pieza_fdi, registrado_en DESC, id
);
