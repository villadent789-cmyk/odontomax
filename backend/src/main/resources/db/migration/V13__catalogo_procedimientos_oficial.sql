-- =============================================================================
-- V13 · Catálogo clínico oficial para "Nuevo Tratamiento Odontológico"
-- =============================================================================
-- tratamientos.procedimiento_id y tratamiento_items.procedimiento_id tienen FK a
-- catalogo_procedimientos: los 23 procedimientos oficiales deben existir aquí para
-- poder registrar tratamientos con ellos. Los ids coinciden con servicios_web (V12)
-- y con frontend/src/data/catalogoProcedimientos.js.
-- Los procedimientos anteriores se DESACTIVAN (no se borran: hay tratamientos que
-- los referencian).
-- =============================================================================

-- 1. Las 6 especialidades oficiales como categorías
INSERT INTO categorias_procedimiento (nombre, orden) VALUES
('Odontología General', 101),
('Endodoncia', 102),
('Prótesis', 103),
('Estética Dental', 104),
('Cirugía Dental', 105),
('Diagnóstico', 106)
ON CONFLICT (nombre) DO NOTHING;

-- 2. Desactivar el catálogo anterior
UPDATE catalogo_procedimientos SET activo = false, actualizado_en = now()
WHERE id NOT LIKE 'srv-%';

-- 3. Los 23 procedimientos oficiales (sin precio: lo fija el odontólogo en cada caso)
INSERT INTO catalogo_procedimientos (id, categoria_id, nombre, descripcion, sesiones_sugeridas, requiere_pieza, orden, activo)
SELECT v.id, c.id, v.nombre, v.descripcion, v.sesiones, v.requiere_pieza, v.orden, true
FROM (VALUES
('srv-eval-odon', 'Odontología General', 'Evaluación Odontológica', 'Diagnóstico clínico inicial, revisión de caries, encías y plan de salud bucal.', 1, false, 1),
('srv-curaciones', 'Odontología General', 'Curaciones', 'Restauración con resina fotocurable estética libre de mercurio.', 1, true, 2),
('srv-profilaxis', 'Odontología General', 'Profilaxis Dental', 'Limpieza profunda con ultrasonido, remoción de sarro y pulido dental.', 1, false, 3),
('srv-rest-estetica', 'Odontología General', 'Restauraciones Estéticas', 'Reconstrucción anatómica y estética en piezas anteriores y posteriores.', 1, true, 4),
('srv-extracciones', 'Odontología General', 'Extracciones Dentales', 'Extracción dental simple bajo anestesia local.', 1, true, 5),
('srv-caries', 'Odontología General', 'Tratamiento de Caries', 'Eliminación conservadora de caries y protección del tejido dental sano.', 1, true, 6),
('srv-sellantes', 'Odontología General', 'Sellantes', 'Protección preventiva en fosas y fisuras de molares.', 1, true, 7),
('srv-endod-trat', 'Endodoncia', 'Tratamiento de Conductos', 'Tratamiento endodóntico para eliminar la infección del nervio y salvar la pieza.', 2, true, 8),
('srv-endod-retrat', 'Endodoncia', 'Retratamiento de Conductos', 'Desobturación y nueva limpieza de conductos con infección persistente.', 3, true, 9),
('srv-coronas', 'Prótesis', 'Coronas', 'Rehabilitación fija con corona de porcelana o zirconio.', 2, true, 10),
('srv-puentes', 'Prótesis', 'Puentes', 'Estructura fija para reemplazar piezas ausentes.', 3, true, 11),
('srv-prot-remov', 'Prótesis', 'Prótesis Removibles', 'Prótesis acrílica o flexible removible.', 3, false, 12),
('srv-prot-implante', 'Prótesis', 'Prótesis sobre Implantes', 'Prótesis fija sobre implantes oseointegrados.', 3, true, 13),
('srv-blanqueamiento', 'Estética Dental', 'Blanqueamiento Dental', 'Aclaramiento dental profesional.', 1, false, 14),
('srv-rest-esteticas', 'Estética Dental', 'Restauraciones Estéticas Avanzadas', 'Carillas y modelado estético directo para armonizar forma y color.', 2, true, 15),
('srv-diseno-sonrisa', 'Estética Dental', 'Diseño de Sonrisa', 'Planificación estética personalizada de la sonrisa.', 2, false, 16),
('srv-extracciones-cir', 'Cirugía Dental', 'Extracciones', 'Extracciones dentales complejas bajo anestesia local.', 1, true, 17),
('srv-terceros-molares', 'Cirugía Dental', 'Extracción de Terceros Molares', 'Cirugía de muelas del juicio impactadas o retenidas.', 1, true, 18),
('srv-cirugia-oral', 'Cirugía Dental', 'Cirugía Oral', 'Cirugía oral menor: frenectomías, regularización de reborde y biopsias.', 1, false, 19),
('srv-eval-integral', 'Diagnóstico', 'Evaluación Integral', 'Examen clínico bucal completo, odontograma y plan preventivo.', 1, false, 20),
('srv-fotos-clinicas', 'Diagnóstico', 'Fotografías Clínicas', 'Registro fotográfico intraoral y extraoral.', 1, false, 21),
('srv-modelos-estudio', 'Diagnóstico', 'Modelos de Estudio', 'Impresión y vaciado de modelos para diagnóstico.', 1, false, 22),
('srv-radiografias', 'Diagnóstico', 'Radiografías Periapicales', 'Toma radiográfica intraoral digital.', 1, true, 23)
) AS v(id, categoria, nombre, descripcion, sesiones, requiere_pieza, orden)
JOIN categorias_procedimiento c ON c.nombre = v.categoria
ON CONFLICT (id) DO UPDATE SET
    categoria_id = EXCLUDED.categoria_id,
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    sesiones_sugeridas = EXCLUDED.sesiones_sugeridas,
    requiere_pieza = EXCLUDED.requiere_pieza,
    orden = EXCLUDED.orden,
    activo = true,
    actualizado_en = now();
