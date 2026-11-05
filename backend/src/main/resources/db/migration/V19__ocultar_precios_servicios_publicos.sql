-- Oculta precios referenciales del catalogo publico de tratamientos.
UPDATE servicios_web
SET precio_desde = NULL
WHERE precio_desde IS NOT NULL;
