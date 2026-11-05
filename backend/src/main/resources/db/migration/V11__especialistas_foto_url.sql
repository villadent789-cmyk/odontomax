-- V11: URL opcional de la foto del doctor (sin foto, el frontend muestra un avatar con sus iniciales).
-- foto_key (V1) queda reservada para fotos subidas al almacenamiento propio.
ALTER TABLE especialistas ADD COLUMN IF NOT EXISTS foto_url text;
