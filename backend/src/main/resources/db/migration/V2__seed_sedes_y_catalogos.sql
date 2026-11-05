-- =============================================================================
-- V2 · Datos iniciales (seed): sedes, horarios, especialistas,
-- catálogo clínico (37), servicios web e inventario base
-- NOTA DE SEGURIDAD: Los usuarios del sistema no se siembran con contraseñas fijas.
-- El usuario ADMIN_GENERAL inicial se inicializa mediante bootstrap seguro con
-- las variables de entorno ADMIN_BOOTSTRAP_EMAIL y ADMIN_BOOTSTRAP_PASSWORD.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Sedes Clínicas Oficiales (Huancayo)
-- -----------------------------------------------------------------------------
INSERT INTO sedes (id, nombre, direccion, ciudad, telefono, whatsapp, email, horario_texto,
                   color, color_secundario, sillones, descripcion, foto_portada, fotos, activa) VALUES
('sede-chilca-1', 'Sede Chilca 1 - Calle Real', 'Calle Real 716 (entre Castilla, a 1 cdra. del mercado Chilca)', 'Huancayo - Chilca', '943 672 062', '51943672062', 'chilca1@odontomax.pe', 'Lun - Sáb: 9:00 a.m. - 8:00 p.m.',
 '#C59B27', '#FEF3C7', 4, 'Sede principal especializada en Ortodoncia, Brackets y Ortopedia Maxilar.', '/sedes/chilca-1/fachada.jpg', ARRAY['/sedes/chilca-1/fachada.jpg', '/sedes/chilca-1/recepcion.jpg', '/sedes/chilca-1/consultorio-1.jpg', '/sedes/chilca-1/consultorio-2.jpg']::text[], true),
('sede-chilca-2', 'Sede Chilca 2 - Ricardo Palma', 'Jr. Ricardo Palma 411 (a 3 cuadras arriba del mercado Chilca)', 'Huancayo - Chilca', '960 112 257', '51960112257', 'chilca2@odontomax.pe', 'Lun - Sáb: 9:00 a.m. - 7:00 p.m.',
 '#B45309', '#FFFBEB', 3, 'Atención odontológica integral, estética dental y ambiente lúdico adaptado para niños y jóvenes.', '/sedes/chilca-2/fachada.jpg', ARRAY['/sedes/chilca-2/fachada.jpg', '/sedes/chilca-2/consultorio.jpg']::text[], true),
('sede-eltambo', 'Sede El Tambo - Mariátegui', 'Prolg. Mariátegui #444 (Al frente de la estación de bomberos - El Tambo)', 'Huancayo - El Tambo', '943 672 062', '51943672062', 'eltambo@odontomax.pe', 'Lun - Sáb: 9:00 a.m. - 8:00 p.m.',
 '#0F172A', '#F1F5F9', 3, 'Centro de estética dental, rehabilitación oral avanzada e implantes.', NULL, '{}'::text[], true)
ON CONFLICT (id) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    direccion = EXCLUDED.direccion,
    telefono = EXCLUDED.telefono,
    whatsapp = EXCLUDED.whatsapp,
    email = EXCLUDED.email;

-- Horarios de atención (lunes a sábado)
INSERT INTO sede_horarios (sede_id, dia_semana, hora_apertura, hora_cierre)
SELECT s.id, d, TIME '09:00', h.cierre
FROM sedes s
JOIN (VALUES ('sede-chilca-1', TIME '20:00'), ('sede-chilca-2', TIME '19:00'), ('sede-eltambo', TIME '20:00')) AS h(sede, cierre) ON h.sede = s.id
CROSS JOIN generate_series(1, 6) AS d
ON CONFLICT (sede_id, dia_semana) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Especialistas Odontológicos y Sedes de Atención
-- -----------------------------------------------------------------------------
INSERT INTO especialistas (id, usuario_id, nombre, especialidad, cop, email, telefono, activo) VALUES
('10000000-0000-0000-0000-000000000001', NULL, 'Dr. Alex Jhoel Ríos Romero', 'Ortodoncia & Ortopedia Maxilar', 'COP-38291', 'admin@odontomax.pe', '943 672 062', true),
('10000000-0000-0000-0000-000000000002', NULL, 'Dra. Thalía Anaís Vilcapoma Palma', 'Odontopediatría & Estética Dental', 'COP-42109', 'dr.vilcapoma@odontomax.pe', '960 112 257', true),
('10000000-0000-0000-0000-000000000003', NULL, 'Dr. Roberto Paz', 'Cirugía Bucal & Maxilofacial', 'COP-29184', 'dr.paz@odontomax.pe', '943 672 062', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO especialista_sedes (especialista_id, sede_id) VALUES
('10000000-0000-0000-0000-000000000001', 'sede-chilca-1'),
('10000000-0000-0000-0000-000000000001', 'sede-chilca-2'),
('10000000-0000-0000-0000-000000000001', 'sede-eltambo'),
('10000000-0000-0000-0000-000000000002', 'sede-chilca-2'),
('10000000-0000-0000-0000-000000000002', 'sede-chilca-1'),
('10000000-0000-0000-0000-000000000003', 'sede-eltambo'),
('10000000-0000-0000-0000-000000000003', 'sede-chilca-1')
ON CONFLICT (especialista_id, sede_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. Categorías del Catálogo Clínico
-- -----------------------------------------------------------------------------
INSERT INTO categorias_procedimiento (nombre, orden) VALUES
('Odontología Restauradora & Estética', 1),
('Ortodoncia & Ortopedia Maxilar', 2),
('Cirugía Bucal & Maxilofacial', 3),
('Endodoncia (Tratamiento de Conductos)', 4),
('Rehabilitación Oral & Prótesis', 5),
('Periodoncia & Prevención', 6),
('Implantología Dental', 7),
('Odontopediatría', 8)
ON CONFLICT (nombre) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. Catálogo Predeterminado de Procedimientos (37)
-- -----------------------------------------------------------------------------
INSERT INTO catalogo_procedimientos (id, categoria_id, nombre, descripcion, sesiones_sugeridas, requiere_pieza, orden)
SELECT v.id, c.id, v.nombre, v.descripcion, v.sesiones, v.requiere_pieza, v.orden
FROM (VALUES
('resina-1-sup', 'Odontología Restauradora & Estética', 'Curación Dental con Resina Fotocurable (1 Superficie)', 'Eliminación de tejido cariado, grabado ácido, adhesivo y resina estética nanoparticulada.', 1, true, 1),
('resina-compuesta', 'Odontología Restauradora & Estética', 'Curación Dental con Resina Fotocurable Compleja (2 a más Superficies)', 'Restauración proximal y oclusal con matrices seccionales y acabado estético anatomizado.', 1, true, 2),
('reconstruccion-perno', 'Odontología Restauradora & Estética', 'Reconstrucción Dental con Perno de Fibra de Vidrio', 'Cementado de perno intra-radicular de fibra y reconstrucción de muñón coronal con resina dual.', 1, true, 3),
('carilla-resina', 'Odontología Restauradora & Estética', 'Carillas Estéticas de Resina de Alta Gama', 'Diseño directo o indirecto de sonrisa por sector anterosuperior con resina microhíbrida.', 2, true, 4),
('carilla-ceramica', 'Odontología Restauradora & Estética', 'Carillas de Cerámica / Disilicato de Litio (E-Max)', 'Láminas cerámicas ultradelgadas confeccionadas en laboratorio para alta estética dental.', 3, true, 5),
('blanqueamiento-clinico', 'Odontología Restauradora & Estética', 'Blanqueamiento Dental Clínico Láser / LED (Consultorio)', 'Aclaramiento dental en consultorio con peróxido de hidrógeno activado por luz LED.', 1, false, 6),
('blanqueamiento-casero', 'Odontología Restauradora & Estética', 'Blanqueamiento Dental Ambulatorio con Férulas Personalizadas', 'Confección de férulas blandas y kit de peróxido de carbamida para aplicación domiciliaria.', 2, false, 7),
('orto-metalicos', 'Ortodoncia & Ortopedia Maxilar', 'Ortodoncia Correctiva con Brackets Metálicos Convencionales', 'Alineación y nivelación dental bimaxilar con aparatología fija metálica.', 18, false, 8),
('orto-autoligados', 'Ortodoncia & Ortopedia Maxilar', 'Ortodoncia con Brackets Autoligados de Baja Fricción', 'Sistema autoligado de menor fricción biológica, citas más rápidas y menor tiempo de tratamiento.', 12, false, 9),
('orto-esteticos', 'Ortodoncia & Ortopedia Maxilar', 'Ortodoncia con Brackets Estéticos (Zafiro / Cerámica)', 'Brackets transparentes de cristal de zafiro o cerámica policristalina de alta estética.', 18, false, 10),
('orto-alineadores', 'Ortodoncia & Ortopedia Maxilar', 'Ortodoncia Invisible con Alineadores Transparentes', 'Corrección ortodóncica mediante placas termoplásticas secuenciales removibles.', 10, false, 11),
('ortopedia-infantil', 'Ortodoncia & Ortopedia Maxilar', 'Ortopedia Funcional de los Maxilares (Odontopediatría / Mixta)', 'Aparatos funcionales o expansores rápidos del paladar (Hyrax, Hass) en etapa de crecimiento.', 8, false, 12),
('retenedores', 'Ortodoncia & Ortopedia Maxilar', 'Retenedores Post-Ortodoncia (Fijos o Placas Hawley / Essix)', 'Fase de contención y estabilidad post-tratamiento con retenedores fijos o removibles.', 2, false, 13),
('cirugia-tercera-molar-simple', 'Cirugía Bucal & Maxilofacial', 'Cirugía de Tercera Molar Erupcionada (Muela del Juicio)', 'Extracción quirúrgica de tercer molar en posición vertical con anestesia local.', 1, true, 14),
('cirugia-tercera-molar-impactada', 'Cirugía Bucal & Maxilofacial', 'Cirugía de Tercera Molar Impactada / Retenida Compleja', 'Odontosección y osteotomía guiada para extracción de tercer molar mesioangulada u horizontal.', 1, true, 15),
('extraccion-simple', 'Cirugía Bucal & Maxilofacial', 'Extracción Dental Simple', 'Avulsión dental con fórceps o elevadores bajo anestesia infiltrativa local.', 1, true, 16),
('extraccion-resto-radicular', 'Cirugía Bucal & Maxilofacial', 'Extracción Dental de Restos Radiculares / Vía Alveolar', 'Colgajo y remoción atraumática de raíz dental fracturada con hemostasia local.', 1, true, 17),
('frenectomia', 'Cirugía Bucal & Maxilofacial', 'Frenectomía Quirúrgica (Lingual o Labial)', 'Liberación quirúrgica de frenillo anormal para resolver diastemas o anquiloglosia.', 1, false, 18),
('endo-unirradicular', 'Endodoncia (Tratamiento de Conductos)', 'Endodoncia Mecanizada Unirradicular (Incisivos / Caninos)', 'Instrumentación rotatoria mecanizada de conducto único y obturación termoplástica tridimensional.', 1, true, 19),
('endo-birradicular', 'Endodoncia (Tratamiento de Conductos)', 'Endodoncia Mecanizada Birradicular (Premolares)', 'Conformación y desinfección de 2 conductos radiculares con irrigación ultrasónica.', 2, true, 20),
('endo-multirradicular', 'Endodoncia (Tratamiento de Conductos)', 'Endodoncia Mecanizada Multirradicular (Molares)', 'Localización anatómica y obturación de 3 a 4 conductos molares con localizador apical electrónico.', 2, true, 21),
('retratamiento-endo', 'Endodoncia (Tratamiento de Conductos)', 'Retratamiento Endodóntico (Desobturación y Re-instrumentación)', 'Remoción de gutapercha antigua, medicación intraconducto con hidróxido de calcio y reobturación.', 3, true, 22),
('corona-metal-porcelana', 'Rehabilitación Oral & Prótesis', 'Corona Dental Metal-Porcelana', 'Prótesis fija con cofia metálica noble recubierta de porcelana feldespática.', 3, true, 23),
('corona-circonio', 'Rehabilitación Oral & Prótesis', 'Corona Dental Libre de Metal en Circonio / Cerámica Pura', 'Corona estética monolítica fresada con tecnología digital CAD/CAM de máxima biocompatibilidad.', 3, true, 24),
('protesis-parcial-removible', 'Rehabilitación Oral & Prótesis', 'Prótesis Parcial Removible (PPR Metálica o Flexible)', 'Estructura con ganchos colados o retenedores estéticos de nylon para reponer múltiples piezas ausentes.', 4, false, 25),
('protesis-total', 'Rehabilitación Oral & Prótesis', 'Prótesis Total Superior o Inferior en Acrílico Termocurado', 'Rehabilitación del paciente edéntulo completo con dientes importados de multicapa y prueba en cera.', 4, false, 26),
('incrustacion-dental', 'Rehabilitación Oral & Prótesis', 'Incrustación Dental Estética (Inlay / Onlay / Overlay)', 'Restauración indirecta en resina compuesta o disilicato para dientes posteriores con amplia pérdida dentaria.', 2, true, 27),
('profilaxis-destartraje', 'Periodoncia & Prevención', 'Profilaxis Dental & Destartraje Ultrasónico Completo', 'Eliminación de cálculo supragingival con ultrasonido piezoeléctrico, pulido coronario con pasta profiláctica y flúor.', 1, false, 28),
('raspaje-curetaje', 'Periodoncia & Prevención', 'Raspaje y Alisado Radicular (Curetaje por Cuadrante)', 'Tratamiento periodontal no quirúrgico de bolsas con curetas Gracey y descontaminación radicular.', 4, false, 29),
('gingivectomia', 'Periodoncia & Prevención', 'Gingivectomía / Gingivoplastía Estética (Recorte de Encía)', 'Remodelado del contorno gingival anterosuperior para corrección de sonrisa gingival.', 1, true, 30),
('fluor-barniz', 'Periodoncia & Prevención', 'Aplicación de Flúor Barniz de Alta Concentración', 'Terapia de remineralización del esmalte y sellado de túbulos dentinarios contra la hipersensibilidad.', 1, false, 31),
('implante-titanio', 'Implantología Dental', 'Implante Dental de Titanio Grado Médico Oseointegrado', 'Fijación quirúrgica intraósea biocompatible para reemplazar raíz dental ausente.', 4, true, 32),
('corona-sobre-implante', 'Implantología Dental', 'Corona Protésica sobre Implante Dental (Atornillada / Cementada)', 'Aditamento protésico intermedio y corona de porcelana personalizada sobre análogo.', 3, true, 33),
('injerto-oseo', 'Implantología Dental', 'Regeneración Ósea Guiada con Injerto Óseo y Membrana', 'Preservación de reborde alveolar o aumento horizontal previo a colocación de implantes.', 1, true, 34),
('pulpotomia-pediatrica', 'Odontopediatría', 'Pulpotomía / Pulpectomía en Diente Deciduo Infantil', 'Terapia pulpar pediátrica en molares primarios con sellado de pasta MTA / ZOE.', 1, true, 35),
('corona-acero-infantil', 'Odontopediatría', 'Corona Metálica de Acero Cromo para Diente Deciduo', 'Restauración protectora total para molares de leche con destrucción coronaria severa.', 1, true, 36),
('sellantes-fosas', 'Odontopediatría', 'Sellantes de Fosas y Fisuras Preventivos (por pieza)', 'Barrera micromecánica de resina fluida en surcos de molares para prevención primaria de caries.', 1, true, 37)
) AS v(id, categoria, nombre, descripcion, sesiones, requiere_pieza, orden)
JOIN categorias_procedimiento c ON c.nombre = v.categoria
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 6. Servicios Reservables por la Web
-- -----------------------------------------------------------------------------
INSERT INTO servicios_web (id, nombre, especialidad, descripcion, duracion_min, precio_desde, orden) VALUES
('srv-1', 'Evaluación Integral y Profilaxis Ultrasónica', 'Odontología General', 'Diagnóstico completo con cámara intraoral, remoción de placa y pulido coronal.', 40, NULL, 1),
('srv-2', 'Ortodoncia (Brackets Autoligados / Alineadores)', 'Ortodoncia', 'Estudio cefalométrico, montaje de aparatología o control mensual de alineación.', 45, NULL, 2),
('srv-3', 'Cirugía de Terceras Molares (Muelas del Juicio)', 'Cirugía Maxilofacial', 'Extracción quirúrgica atraumática con sutura reabsorbible y anestesia local.', 60, NULL, 3),
('srv-4', 'Endodoncia Mecanizada Multirradicular', 'Endodoncia', 'Tratamiento de conductos con localizador apical y sellado termoplástico.', 90, NULL, 4),
('srv-5', 'Blanqueamiento Dental Láser / LED', 'Estética Dental', 'Aclaramiento dental en una sola sesión de 3 aplicaciones de gel al 35%.', 60, NULL, 5),
('srv-6', 'Implante Dental de Titanio Grado Médico', 'Implantología', 'Fijación biológica osteointegrada de titanio premium con aditamento protésico.', 75, NULL, 6)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Inventario Base y Stock Inicial por Sede
-- -----------------------------------------------------------------------------
INSERT INTO productos_inventario (id, codigo, nombre, categoria, unidad, precio_unitario, stock_minimo, activo) VALUES
('20000000-0000-0000-0000-000000000001', 'MAT-001', 'Resina Compuesta 3M Filtek Z250 (A2)', 'Restauración y Operatoria', 'Jeringa 4g', 85.00, 8, true),
('20000000-0000-0000-0000-000000000002', 'MAT-002', 'Anestesia Lidocaína 2% con Epinefrina (Zeyco)', 'Anestésicos y Farmacología', 'Caja x 50 cartuchos', 110.00, 5, true),
('20000000-0000-0000-0000-000000000003', 'MAT-003', 'Guantes de Nitrilo Quirúrgico (Talla M)', 'Bioseguridad y Protección', 'Caja x 100 uds', 32.00, 15, true),
('20000000-0000-0000-0000-000000000004', 'MAT-004', 'Brackets Autoligados Metálicos Roth 0.22', 'Ortodoncia Especializada', 'Kit 5x5', 290.00, 4, true),
('20000000-0000-0000-0000-000000000005', 'MAT-005', 'Implante Dental Neodent Drive 3.75x11.5mm', 'Implantología Quirúrgica', 'Unidad estéril', 420.00, 3, true)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO inventario_stock (producto_id, sede_id, cantidad) VALUES
('20000000-0000-0000-0000-000000000001', 'sede-chilca-1', 14),
('20000000-0000-0000-0000-000000000001', 'sede-chilca-2', 3),
('20000000-0000-0000-0000-000000000001', 'sede-eltambo', 9),
('20000000-0000-0000-0000-000000000002', 'sede-chilca-1', 8),
('20000000-0000-0000-0000-000000000002', 'sede-chilca-2', 6),
('20000000-0000-0000-0000-000000000002', 'sede-eltambo', 2),
('20000000-0000-0000-0000-000000000003', 'sede-chilca-1', 28),
('20000000-0000-0000-0000-000000000003', 'sede-chilca-2', 18),
('20000000-0000-0000-0000-000000000003', 'sede-eltambo', 22),
('20000000-0000-0000-0000-000000000004', 'sede-chilca-1', 6),
('20000000-0000-0000-0000-000000000004', 'sede-chilca-2', 1),
('20000000-0000-0000-0000-000000000004', 'sede-eltambo', 5),
('20000000-0000-0000-0000-000000000005', 'sede-chilca-1', 5),
('20000000-0000-0000-0000-000000000005', 'sede-chilca-2', 0),
('20000000-0000-0000-0000-000000000005', 'sede-eltambo', 7)
ON CONFLICT (producto_id, sede_id) DO UPDATE SET cantidad = EXCLUDED.cantidad;

-- Sin sesión de caja semilla: debe abrirla un usuario real desde la aplicación
-- (abierta_por es FK a usuarios y ninguna migración crea usuarios).
