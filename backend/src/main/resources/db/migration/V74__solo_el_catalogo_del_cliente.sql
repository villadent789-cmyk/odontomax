-- El catálogo del sistema y de la web es solo el tarifario que entregó el cliente (V52 para Villadent; los estudios de
-- Villamax ya traen sus precios). Los servicios genéricos de ejemplo (srv-*) se quitan.

-- 1. Cada servicio del tarifario debe tener su formulario clínico (un servicio, un formulario). Casi todos ya lo tienen; esto
--    solo completa los que falten, sin tocar los que ya están.
INSERT INTO servicio_plantilla (servicio_id, plantilla_codigo)
SELECT id, codigo
FROM (
    SELECT id,
           CASE
               WHEN id = 'tar-exo-ninos' THEN 'ODONTOPEDIATRIA'
               WHEN id IN ('tar-exo-3ra-erupcionada', 'tar-exo-3ra-retenida') THEN 'CIRUGIA_ORAL'
               WHEN especialidad = 'Exodoncias' THEN 'EXODONCIA'
               WHEN especialidad = 'Consulta y medicación' THEN 'CONSULTA'
               WHEN especialidad = 'Restauraciones' THEN 'RESTAURACION'
               WHEN id LIKE 'tar-ppr-%' OR id LIKE 'tar-total-%' THEN 'PROTESIS_REMOVIBLE'
               WHEN especialidad = 'Prótesis' THEN 'PROTESIS_FIJA'
               WHEN id = 'tar-blanqueamiento' THEN 'BLANQUEAMIENTO'
               WHEN especialidad = 'Limpieza y prevención' THEN 'PREVENCION'
               WHEN id LIKE 'tar-marpe-%' OR id = 'tar-mascara-petit' THEN 'ORTOPEDIA'
               WHEN especialidad = 'Ortodoncia' THEN 'ORTODONCIA'
               WHEN especialidad IN ('Endodoncia', 'Perno de fibra de vidrio') THEN 'ENDODONCIA'
           END AS codigo
    FROM servicios_web
    WHERE id LIKE 'tar-%'
) m
WHERE codigo IS NOT NULL
ON CONFLICT (servicio_id) DO NOTHING;

-- 2. La web (servicios y reserva) muestra el tarifario del cliente.
UPDATE servicios_web SET visible_en_reserva = true WHERE id LIKE 'tar-%';

-- 3. Se quitan los servicios genéricos. Si alguna cita ya los usa, se desactivan y se ocultan (no se pierde la cita).
DELETE FROM servicios_web s
WHERE s.id LIKE 'srv-%'
  AND NOT EXISTS (SELECT 1 FROM citas c WHERE c.servicio_id = s.id);

UPDATE servicios_web SET activo = false, visible_en_reserva = false WHERE id LIKE 'srv-%';

-- 4. Esa nota era interna (V52) y salía como descripción pública de los estudios de Villamax.
UPDATE servicios_web SET descripcion = NULL WHERE descripcion = 'Tarifario del cliente.';
