-- Tarifario entregado por el cliente (lista de costos odontológicos).
-- Tratamientos dentales -> VILLADENT. Rayos X y tomografía -> VILLAMAX (centro radiológico).
-- Los precios incluyen IGV. Los de rango guardan el mínimo y la descripción indica el máximo.

ALTER TABLE servicios_web ADD COLUMN IF NOT EXISTS visible_en_reserva boolean NOT NULL DEFAULT true;

-- El tarifario detallado se usa en planes y cobros; la reserva web conserva sus servicios generales.
INSERT INTO servicios_web (id, nombre, especialidad, descripcion, duracion_min, precio_desde, activo, orden,
                           unidad_de_negocio, comision_porcentaje, visible_en_reserva)
VALUES
    ('tar-consulta-normal', 'Consulta normal', 'Consulta y medicación', NULL, 20, 20.00, true, 100, 'VILLADENT', NULL, false),
    ('tar-consulta-especializada', 'Consulta especializada', 'Consulta y medicación', NULL, 30, 50.00, true, 101, 'VILLADENT', NULL, false),
    ('tar-medicacion', 'Medicación', 'Consulta y medicación', NULL, 15, 20.00, true, 102, 'VILLADENT', NULL, false),
    ('tar-liss-simple', 'Restauración LISS simple', 'Restauraciones', NULL, 30, 60.00, true, 103, 'VILLADENT', NULL, false),
    ('tar-liss-compuesta', 'Restauración LISS compuesta', 'Restauraciones', NULL, 40, 70.00, true, 104, 'VILLADENT', NULL, false),
    ('tar-liss-compleja', 'Restauración LISS compleja', 'Restauraciones', NULL, 50, 90.00, true, 105, 'VILLADENT', NULL, false),
    ('tar-beautifil-simple', 'Restauración Beautifil simple', 'Restauraciones', NULL, 30, 70.00, true, 106, 'VILLADENT', NULL, false),
    ('tar-beautifil-compuesta', 'Restauración Beautifil compuesta', 'Restauraciones', NULL, 40, 90.00, true, 107, 'VILLADENT', NULL, false),
    ('tar-beautifil-compleja', 'Restauración Beautifil compleja', 'Restauraciones', 'Precio desde S/ 90; según la complejidad puede llegar a S/ 120.', 50, 90.00, true, 108, 'VILLADENT', NULL, false),
    ('tar-exo-ninos', 'Exodoncia en niños', 'Exodoncias', NULL, 30, 50.00, true, 109, 'VILLADENT', NULL, false),
    ('tar-exo-incisivos', 'Exodoncia de incisivos', 'Exodoncias', NULL, 30, 60.00, true, 110, 'VILLADENT', NULL, false),
    ('tar-exo-molares', 'Exodoncia de molares', 'Exodoncias', NULL, 40, 60.00, true, 111, 'VILLADENT', NULL, false),
    ('tar-exo-3ra-erupcionada', 'Exodoncia de 3ra molar erupcionada', 'Exodoncias', NULL, 45, 120.00, true, 112, 'VILLADENT', NULL, false),
    ('tar-exo-3ra-retenida', 'Exodoncia de 3ra molar retenida', 'Exodoncias', 'Precio desde S/ 200; según la dificultad puede llegar a S/ 380.', 60, 200.00, true, 113, 'VILLADENT', NULL, false),
    ('tar-sutura', 'Sutura', 'Exodoncias', NULL, 15, 20.00, true, 114, 'VILLADENT', NULL, false),
    ('tar-ppr-olimpico', 'Prótesis parcial removible (PPR) por arcada – Olímpico', 'Prótesis', NULL, 45, 750.00, true, 115, 'VILLADENT', NULL, false),
    ('tar-ppr-ortolux', 'Prótesis parcial removible (PPR) por arcada – Ortolux', 'Prótesis', NULL, 45, 950.00, true, 116, 'VILLADENT', NULL, false),
    ('tar-ppr-ivoestar', 'Prótesis parcial removible (PPR) por arcada – Ivoestar', 'Prótesis', NULL, 45, 1150.00, true, 117, 'VILLADENT', NULL, false),
    ('tar-ppr-porcelana', 'Prótesis parcial removible (PPR) por arcada – Porcelana', 'Prótesis', NULL, 45, 2000.00, true, 118, 'VILLADENT', NULL, false),
    ('tar-total-olimpico', 'Prótesis total por arcada – Olímpico', 'Prótesis', NULL, 60, 1500.00, true, 119, 'VILLADENT', NULL, false),
    ('tar-total-ortolux', 'Prótesis total por arcada – Ortolux', 'Prótesis', NULL, 60, 1700.00, true, 120, 'VILLADENT', NULL, false),
    ('tar-total-ivoestar', 'Prótesis total por arcada – Ivoestar', 'Prótesis', NULL, 60, 1900.00, true, 121, 'VILLADENT', NULL, false),
    ('tar-fija-acrilico', 'Prótesis fija – Acrílico', 'Prótesis', NULL, 45, 100.00, true, 122, 'VILLADENT', NULL, false),
    ('tar-fija-ivocrom', 'Prótesis fija – Ivocrom', 'Prótesis', NULL, 45, 250.00, true, 123, 'VILLADENT', NULL, false),
    ('tar-fija-porcelana', 'Prótesis fija – Porcelana', 'Prótesis', NULL, 45, 400.00, true, 124, 'VILLADENT', NULL, false),
    ('tar-fija-zirconio', 'Prótesis fija – Zirconio', 'Prótesis', NULL, 45, 700.00, true, 125, 'VILLADENT', NULL, false),
    ('tar-carilla-resina', 'Carilla por pieza – Resina', 'Prótesis', NULL, 45, 450.00, true, 126, 'VILLADENT', NULL, false),
    ('tar-carilla-porcelana', 'Carilla por pieza – Porcelana', 'Prótesis', NULL, 45, 700.00, true, 127, 'VILLADENT', NULL, false),
    ('tar-carilla-disilicato', 'Carilla por pieza – Disilicato', 'Prótesis', NULL, 45, 850.00, true, 128, 'VILLADENT', NULL, false),
    ('tar-carilla-zirconio', 'Carilla por pieza – Zirconio', 'Prótesis', NULL, 45, 1000.00, true, 129, 'VILLADENT', NULL, false),
    ('tar-destartraje', 'Destartraje', 'Limpieza y prevención', NULL, 40, 80.00, true, 130, 'VILLADENT', NULL, false),
    ('tar-profilaxis', 'Profilaxis', 'Limpieza y prevención', NULL, 40, 70.00, true, 131, 'VILLADENT', NULL, false),
    ('tar-sellantes', 'Sellantes', 'Limpieza y prevención', NULL, 20, 40.00, true, 132, 'VILLADENT', NULL, false),
    ('tar-fluor-barniz', 'Flúor barniz', 'Limpieza y prevención', NULL, 20, 80.00, true, 133, 'VILLADENT', NULL, false),
    ('tar-fluor-gel', 'Flúor gel', 'Limpieza y prevención', NULL, 20, 60.00, true, 134, 'VILLADENT', NULL, false),
    ('tar-blanqueamiento', 'Blanqueamiento', 'Limpieza y prevención', NULL, 60, 250.00, true, 135, 'VILLADENT', NULL, false),
    ('tar-marpe-6', 'Marpe – 6 tornillos', 'Ortodoncia', NULL, 60, 5000.00, true, 136, 'VILLADENT', NULL, false),
    ('tar-marpe-4', 'Marpe – 4 tornillos', 'Ortodoncia', NULL, 60, 4500.00, true, 137, 'VILLADENT', NULL, false),
    ('tar-mascara-petit', 'Máscara de Petit', 'Ortodoncia', NULL, 45, 1000.00, true, 138, 'VILLADENT', NULL, false),
    ('tar-orto-convencional-inicial', 'Ortodoncia convencional – cuota inicial', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 60, 600.00, true, 139, 'VILLADENT', NULL, false),
    ('tar-orto-convencional-mensual', 'Ortodoncia convencional – cuota mensual', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 30, 120.00, true, 140, 'VILLADENT', NULL, false),
    ('tar-orto-autoligados-inicial', 'Ortodoncia autoligados – cuota inicial', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 60, 1200.00, true, 141, 'VILLADENT', NULL, false),
    ('tar-orto-autoligados-mensual', 'Ortodoncia autoligados – cuota mensual', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 30, 150.00, true, 142, 'VILLADENT', NULL, false),
    ('tar-orto-pitts21-inicial', 'Ortodoncia Pitts 21 – cuota inicial', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 60, 3000.00, true, 143, 'VILLADENT', NULL, false),
    ('tar-orto-pitts21-mensual', 'Ortodoncia Pitts 21 – cuota mensual', 'Ortodoncia', 'Pago de cuotas provisional hasta que exista el plan en cuotas.', 30, 200.00, true, 144, 'VILLADENT', NULL, false),
    ('tar-endo-mono', 'Endodoncia monorradicular', 'Endodoncia', NULL, 60, 300.00, true, 145, 'VILLADENT', NULL, false),
    ('tar-endo-multi', 'Endodoncia multirradicular', 'Endodoncia', NULL, 90, 550.00, true, 146, 'VILLADENT', NULL, false),
    ('tar-endo-pulpectomia', 'Pulpectomía', 'Endodoncia', NULL, 45, 200.00, true, 147, 'VILLADENT', NULL, false),
    ('tar-endo-retratamiento', 'Retratamiento de conductos', 'Endodoncia', NULL, 90, 650.00, true, 148, 'VILLADENT', NULL, false),
    ('tar-perno-fibra', 'Perno de fibra de vidrio', 'Perno de fibra de vidrio', NULL, 30, 120.00, true, 149, 'VILLADENT', NULL, false)
ON CONFLICT (id) DO NOTHING;

-- Villamax: los precios del cliente reemplazan a los de ejemplo, y se agregan los estudios que faltaban.
UPDATE servicios_web SET precio_desde = 50.00, descripcion = 'Tarifario del cliente.' WHERE id = 'vmx-panoramica';
UPDATE servicios_web SET precio_desde = 50.00, descripcion = 'Tarifario del cliente.' WHERE id = 'vmx-cefalometrica';
UPDATE servicios_web SET precio_desde = 20.00, descripcion = 'Tarifario del cliente.' WHERE id = 'vmx-periapical';
UPDATE servicios_web SET precio_desde = 190.00, descripcion = 'Tarifario del cliente.' WHERE id = 'vmx-tomografia-3d';

INSERT INTO servicios_web (id, nombre, especialidad, descripcion, duracion_min, precio_desde, activo, orden,
                           unidad_de_negocio, comision_porcentaje)
VALUES
    ('vmx-bite-wing', 'Radiografía bite wing', 'Radiología', 'Tarifario del cliente. Comisión de ejemplo: ajústala en Administración.', 10, 20.00, true, 5, 'VILLAMAX', 10.00),
    ('vmx-oclusal', 'Radiografía oclusal', 'Radiología', 'Tarifario del cliente. Comisión de ejemplo: ajústala en Administración.', 10, 30.00, true, 6, 'VILLAMAX', 10.00),
    ('vmx-carpal', 'Radiografía carpal', 'Radiología', 'Tarifario del cliente. Comisión de ejemplo: ajústala en Administración.', 15, 50.00, true, 7, 'VILLAMAX', 10.00)
ON CONFLICT (id) DO NOTHING;
