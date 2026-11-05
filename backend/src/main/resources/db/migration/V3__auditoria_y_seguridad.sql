CREATE TABLE IF NOT EXISTS auditoria_log (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    usuario_id      uuid,
    usuario_nombre  text,
    rol             text,
    sede_id         text,
    modulo          text NOT NULL,
    accion          text NOT NULL,
    tabla           text,
    registro_id     text,
    datos_antes     jsonb,
    datos_despues   jsonb,
    detalle         text,
    ip              inet
);

CREATE INDEX IF NOT EXISTS auditoria_fecha_idx ON auditoria_log (creado_en DESC);
CREATE INDEX IF NOT EXISTS auditoria_modulo_idx ON auditoria_log (modulo, creado_en DESC);
CREATE INDEX IF NOT EXISTS auditoria_registro_idx ON auditoria_log (tabla, registro_id);

CREATE OR REPLACE FUNCTION auditoria_inmutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'auditoria_log es inmutable: no se permite % ', TG_OP;
END $$;

DROP TRIGGER IF EXISTS auditoria_no_modificar ON auditoria_log;
CREATE TRIGGER auditoria_no_modificar BEFORE UPDATE OR DELETE ON auditoria_log
    FOR EACH ROW EXECUTE FUNCTION auditoria_inmutable();

DROP TRIGGER IF EXISTS auditoria_no_truncar ON auditoria_log;
CREATE TRIGGER auditoria_no_truncar BEFORE TRUNCATE ON auditoria_log
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_inmutable();

CREATE OR REPLACE FUNCTION auditar_cambio() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_id text;
    v_user_id uuid;
    v_user_nombre text;
    v_rol text;
    v_sede_id text;
    v_ip inet;
BEGIN
    IF TG_OP = 'UPDATE' AND to_jsonb(OLD) = to_jsonb(NEW) THEN
        RETURN NULL;
    END IF;

    v_id := COALESCE(to_jsonb(NEW)->>'id', to_jsonb(OLD)->>'id');

    BEGIN
        v_user_id := nullif(current_setting('app.usuario_id', true), '')::uuid;
    EXCEPTION WHEN OTHERS THEN
        v_user_id := NULL;
    END;

    BEGIN
        v_user_nombre := COALESCE(nullif(current_setting('app.usuario_nombre', true), ''), 'SISTEMA');
    EXCEPTION WHEN OTHERS THEN
        v_user_nombre := 'SISTEMA';
    END;

    BEGIN
        v_rol := nullif(current_setting('app.rol', true), '');
    EXCEPTION WHEN OTHERS THEN
        v_rol := NULL;
    END;

    BEGIN
        v_sede_id := nullif(current_setting('app.sede_id', true), '');
    EXCEPTION WHEN OTHERS THEN
        v_sede_id := NULL;
    END;

    BEGIN
        v_ip := nullif(current_setting('app.ip', true), '')::inet;
    EXCEPTION WHEN OTHERS THEN
        v_ip := NULL;
    END;

    INSERT INTO auditoria_log (usuario_id, usuario_nombre, rol, sede_id, modulo, accion,
                                tabla, registro_id, datos_antes, datos_despues, ip)
    VALUES (v_user_id,
            v_user_nombre,
            v_rol,
            v_sede_id,
            TG_ARGV[0],
            TG_OP,
            TG_TABLE_NAME,
            v_id,
            CASE WHEN TG_OP IN ('UPDATE','DELETE') THEN to_jsonb(OLD) END,
            CASE WHEN TG_OP IN ('INSERT','UPDATE') THEN to_jsonb(NEW) END,
            v_ip);
    RETURN NULL;
END $$;

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('pacientes',                'HISTORIA_CLINICA'),
        ('odontograma_registros',    'ODONTOGRAMA'),
        ('tratamientos',             'HISTORIA_CLINICA'),
        ('radiografias_estudios',    'HISTORIA_CLINICA'),
        ('consentimientos',          'HISTORIA_CLINICA'),
        ('citas',                    'CITAS'),
        ('pagos',                    'FINANZAS'),
        ('gastos',                   'FINANZAS'),
        ('caja_sesiones',            'FINANZAS'),
        ('productos_inventario',     'INVENTARIO'),
        ('inventario_movimientos',   'INVENTARIO'),
        ('transferencias_inventario','INVENTARIO'),
        ('usuarios',                 'CONFIGURACION'),
        ('especialistas',            'CONFIGURACION'),
        ('catalogo_procedimientos',  'CONFIGURACION'),
        ('sedes',                    'CONFIGURACION')
    ) AS x(tabla, modulo)
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS %I_auditoria ON %I', r.tabla, r.tabla);
        EXECUTE format('CREATE TRIGGER %I_auditoria AFTER INSERT OR UPDATE OR DELETE ON %I
                        FOR EACH ROW EXECUTE FUNCTION auditar_cambio(%L)',
                       r.tabla, r.tabla, r.modulo);
    END LOOP;
END $$;

DO $$
DECLARE
    r record;
BEGIN
    -- Excluir la tabla de control de Flyway: Flyway la mantiene bloqueada durante la migración
    -- y el ALTER TABLE quedaría esperando ese bloqueo indefinidamente.
    FOR r IN SELECT tablename FROM pg_tables
             WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', r.tablename);
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated';
    END IF;
END $$;
