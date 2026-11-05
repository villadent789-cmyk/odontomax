-- =============================================================================
-- V12 · Catálogo Oficial de Procedimientos Odontológicos OdontoMax
-- =============================================================================
-- Actualiza la tabla de servicios web y públicos con las 6 especialidades oficiales
-- y sus 23 procedimientos clínicos reales de OdontoMax (Huancayo).
-- =============================================================================

-- Desactivar cualquier servicio obsoleto anterior
UPDATE servicios_web SET activo = false WHERE true;

-- Upsert de los 23 procedimientos oficiales
INSERT INTO servicios_web (id, nombre, especialidad, descripcion, duracion_min, precio_desde, activo, orden) VALUES
-- 1. ODONTOLOGÍA GENERAL
('srv-eval-odon', 'Evaluación Odontológica', 'Odontología General', 'Diagnóstico clínico inicial, revisión de caries, encías y plan de salud bucal.', 30, 0.00, true, 1),
('srv-curaciones', 'Curaciones', 'Odontología General', 'Restauración con resina fotocurable estética de alta resistencia libre de mercurio.', 30, 50.00, true, 2),
('srv-profilaxis', 'Profilaxis Dental', 'Odontología General', 'Limpieza profunda con ultrasonido, remoción de sarro y pulido dental.', 40, 60.00, true, 3),
('srv-rest-estetica', 'Restauraciones Estéticas', 'Odontología General', 'Reconstrucción anatómica y estética en piezas anteriores y posteriores.', 45, 80.00, true, 4),
('srv-extracciones', 'Extracciones Dentales', 'Odontología General', 'Extracción dental simple bajo anestesia local indolora y protocolo estéril.', 30, 50.00, true, 5),
('srv-caries', 'Tratamiento de Caries', 'Odontología General', 'Eliminación conservadora de caries y protección del tejido dental sano.', 30, 50.00, true, 6),
('srv-sellantes', 'Sellantes', 'Odontología General', 'Protección preventiva en fosas y fisuras de molares para evitar caries.', 20, 35.00, true, 7),

-- 2. ENDODONCIA
('srv-endod-trat', 'Tratamiento de Conductos', 'Endodoncia', 'Tratamiento endodóntico mecanizado para eliminar infección del nervio y salvar la pieza.', 60, 200.00, true, 8),
('srv-endod-retrat', 'Retratamiento de Conductos', 'Endodoncia', 'Limpieza profunda y desobturación de conductos para resolver infecciones persistentes.', 60, 250.00, true, 9),

-- 3. PRÓTESIS
('srv-coronas', 'Coronas', 'Prótesis', 'Rehabilitación fija con corona estética de porcelana o zirconio de máxima durabilidad.', 45, 350.00, true, 10),
('srv-puentes', 'Puentes', 'Prótesis', 'Estructura fija para reemplazar piezas ausentes devolviendo la masticación y sonrisa.', 60, 600.00, true, 11),
('srv-prot-remov', 'Prótesis Removibles', 'Prótesis', 'Prótesis acrílica o flexible de fácil adaptación a la boca.', 45, 400.00, true, 12),
('srv-prot-implante', 'Prótesis sobre Implantes', 'Prótesis', 'Fijación de prótesis sobre implantes oseointegrados para máxima firmeza.', 60, 800.00, true, 13),

-- 4. ESTÉTICA DENTAL
('srv-blanqueamiento', 'Blanqueamiento Dental', 'Estética Dental', 'Aclaramiento profesional con tecnología LED fría para una sonrisa brillante.', 45, 180.00, true, 14),
('srv-rest-esteticas', 'Restauraciones Estéticas', 'Estética Dental', 'Carillas y modelado estético directo para armonizar forma y color.', 45, 120.00, true, 15),
('srv-diseno-sonrisa', 'Diseño de Sonrisa', 'Estética Dental', 'Planificación estética personalizada para lograr una sonrisa perfecta y armónica.', 60, 300.00, true, 16),

-- 5. CIRUGÍA DENTAL
('srv-extracciones-cir', 'Extracciones', 'Cirugía Dental', 'Extracciones dentales complejas bajo anestesia local indolora.', 30, 50.00, true, 17),
('srv-terceros-molares', 'Extracción de Terceros Molares', 'Cirugía Dental', 'Cirugía menor de muelas del juicio impactadas o retenidas con sutura estéril.', 60, 180.00, true, 18),
('srv-cirugia-oral', 'Cirugía Oral', 'Cirugía Dental', 'Cirugía oral menor, frenectomías, regularización de reborde y biopsias.', 45, 120.00, true, 19),

-- 6. DIAGNÓSTICO
('srv-eval-integral', 'Evaluación Integral', 'Diagnóstico', 'Examen clínico bucal completo, odontograma digital y plan preventivo.', 30, 0.00, true, 20),
('srv-fotos-clinicas', 'Fotografías Clínicas', 'Diagnóstico', 'Registro fotográfico intraoral y extraoral en alta resolución.', 20, 20.00, true, 21),
('srv-modelos-estudio', 'Modelos de Estudio', 'Diagnóstico', 'Impresión y vaciado de modelos anatómicos para diagnóstico oclusal.', 30, 40.00, true, 22),
('srv-radiografias', 'Radiografías Periapicales', 'Diagnóstico', 'Toma radiográfica intraoral digital de alta precisión con mínima radiación.', 15, 15.00, true, 23)

ON CONFLICT (id) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    especialidad = EXCLUDED.especialidad,
    descripcion = EXCLUDED.descripcion,
    duracion_min = EXCLUDED.duracion_min,
    precio_desde = EXCLUDED.precio_desde,
    activo = EXCLUDED.activo,
    orden = EXCLUDED.orden;
