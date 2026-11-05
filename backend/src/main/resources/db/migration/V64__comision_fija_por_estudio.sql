-- Comisión del doctor externo de Villamax: monto fijo por estudio (S/ 10 por radiografía, S/ 40 por tomografía),
-- multiplicado por la cantidad de estudios. El paciente estudiante tiene convenio: precio manual y sin comisión.
ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS comision_fija numeric(10,2);

UPDATE servicios_web SET comision_fija = 10.00
 WHERE unidad_de_negocio = 'VILLAMAX' AND nombre ILIKE 'Radiograf%' AND comision_fija IS NULL;
UPDATE servicios_web SET comision_fija = 40.00
 WHERE unidad_de_negocio = 'VILLAMAX' AND nombre ILIKE 'Tomograf%' AND comision_fija IS NULL;

ALTER TABLE comisiones_radiologicas ADD COLUMN IF NOT EXISTS cantidad integer NOT NULL DEFAULT 1;
ALTER TABLE comisiones_radiologicas ADD COLUMN IF NOT EXISTS es_estudiante boolean NOT NULL DEFAULT false;
