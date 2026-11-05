-- =============================================================================
-- V30: Eliminar definitivamente sede inexistente sede-chilca-2 (Sede Chilca 2 - Ricardo Palma)
-- Reasignar cualquier dato asociado a la sede oficial Villadent Chilca (sede-chilca-1)
-- =============================================================================

-- 1. Reasignar entidades hacia la sede oficial sede-chilca-1
UPDATE usuarios SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE citas SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE pacientes SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE tratamientos SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE pagos SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE gastos SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE inventario_movimientos SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE radiografias_estudios SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
UPDATE ordenes_laboratorio SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'promociones_paciente') THEN
        UPDATE promociones_paciente SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';
    END IF;
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'configuracion_promociones') THEN
        DELETE FROM configuracion_promociones WHERE sede_id = 'sede-chilca-2';
    END IF;
END $$;

-- Cerrar sesiones abiertas en sede-chilca-2 para no violar la restricción de una sola caja abierta por sede
UPDATE caja_sesiones
SET cerrada_en = COALESCE(cerrada_en, NOW()),
    cerrada_por = COALESCE(cerrada_por, abierta_por),
    monto_esperado = COALESCE(monto_esperado, monto_apertura),
    monto_contado = COALESCE(monto_contado, monto_apertura),
    observaciones = CASE 
        WHEN observaciones IS NULL THEN '[Cierre automático por unificación de sedes]' 
        ELSE observaciones || ' [Cierre automático por unificación de sedes]' 
    END
WHERE sede_id = 'sede-chilca-2' AND cerrada_en IS NULL;

UPDATE caja_sesiones SET sede_id = 'sede-chilca-1' WHERE sede_id = 'sede-chilca-2';

-- 2. Limpiar tablas puente y dependientes de sede-chilca-2
DELETE FROM transferencias_inventario WHERE sede_origen_id = 'sede-chilca-2' OR sede_destino_id = 'sede-chilca-2';
DELETE FROM sede_horarios WHERE sede_id = 'sede-chilca-2';
DELETE FROM especialista_sedes WHERE sede_id = 'sede-chilca-2';
DELETE FROM especialista_horarios WHERE sede_id = 'sede-chilca-2';
DELETE FROM inventario_stock WHERE sede_id = 'sede-chilca-2';

-- 3. Eliminar definitivamente la sede inexistente
DELETE FROM sedes WHERE id = 'sede-chilca-2';
