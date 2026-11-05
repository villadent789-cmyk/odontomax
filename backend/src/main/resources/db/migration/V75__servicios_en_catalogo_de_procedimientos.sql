-- Los planes de tratamiento (tratamientos y tratamiento_items) apuntan por FK a catalogo_procedimientos, pero desde V74 el
-- catálogo vigente es el tarifario de servicios_web (ids tar-*). Sin esta correspondencia, crear un plan con un servicio del
-- tarifario fallaba con "referencia a un registro inexistente". Aquí cada servicio de servicios_web tiene su fila en
-- catalogo_procedimientos (con el mismo id), tanto los actuales como los que se creen después.

CREATE OR REPLACE FUNCTION sincronizar_servicio_en_catalogo() RETURNS trigger AS $$
DECLARE
    v_categoria smallint;
    v_nombre text;
BEGIN
    INSERT INTO categorias_procedimiento (nombre, orden) VALUES (NEW.especialidad, 200)
    ON CONFLICT (nombre) DO NOTHING;
    SELECT id INTO v_categoria FROM categorias_procedimiento WHERE nombre = NEW.especialidad;

    -- El nombre del catálogo es único, pero servicios_web admite dos con el mismo nombre (p. ej. uno antiguo desactivado y el
    -- del tarifario). Si otro procedimiento inactivo tiene este nombre se le añade su id; si el que choca está activo, el
    -- nuevo es el que lleva el id en el nombre.
    UPDATE catalogo_procedimientos
       SET nombre = nombre || ' (' || id || ')', actualizado_en = now()
     WHERE nombre = NEW.nombre AND id <> NEW.id AND NOT activo;
    v_nombre := NEW.nombre;
    IF EXISTS (SELECT 1 FROM catalogo_procedimientos WHERE nombre = v_nombre AND id <> NEW.id) THEN
        v_nombre := NEW.nombre || ' (' || NEW.id || ')';
    END IF;

    INSERT INTO catalogo_procedimientos (id, categoria_id, nombre, descripcion, precio_referencial, activo, orden)
    VALUES (NEW.id, v_categoria, v_nombre, NEW.descripcion, NEW.precio_desde, NEW.activo, NEW.orden)
    ON CONFLICT (id) DO UPDATE
       SET nombre = EXCLUDED.nombre,
           descripcion = EXCLUDED.descripcion,
           precio_referencial = EXCLUDED.precio_referencial,
           activo = EXCLUDED.activo,
           actualizado_en = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- catalogo_procedimientos pasa a ser un espejo de servicios_web: los cambios ya se auditan en servicios_web (CREAR/EDITAR/
-- DESACTIVAR/ACTIVAR_SERVICIO), así que el registro automático de la tabla espejo solo duplicaría cada cambio.
DROP TRIGGER IF EXISTS catalogo_procedimientos_auditoria ON catalogo_procedimientos;

DROP TRIGGER IF EXISTS trg_servicio_en_catalogo ON servicios_web;
CREATE TRIGGER trg_servicio_en_catalogo
    AFTER INSERT OR UPDATE OF nombre, descripcion, precio_desde, activo, orden, especialidad ON servicios_web
    FOR EACH ROW EXECUTE FUNCTION sincronizar_servicio_en_catalogo();

-- Los servicios que ya existen: un UPDATE sin cambio real de valores dispara la sincronización.
UPDATE servicios_web SET activo = activo;
