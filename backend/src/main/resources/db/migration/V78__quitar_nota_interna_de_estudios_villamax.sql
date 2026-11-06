-- Los estudios de Villamax agregados en V52 (bite wing, oclusal y carpal) traían una nota interna como descripción:
-- «Tarifario del cliente. Comisión de ejemplo: ajústala en Administración.». La web pública la mostraba a los pacientes.
-- V74 ya quitó la versión corta de esa nota; esto quita la larga. La comisión no se toca: solo el texto visible.
UPDATE servicios_web
SET descripcion = NULL
WHERE descripcion LIKE 'Tarifario del cliente.%'
   OR descripcion LIKE '%Comisión de ejemplo%';
