-- =============================================================================
-- V10: PORTABILIDAD MULTI-PROVEEDOR POSTGRESQL (SUPABASE, RENDER, NEON, RDS)
-- Protege y aísla permisos de roles específicos de Supabase (anon, authenticated, service_role)
-- utilizando SQL dinámico condicional (EXECUTE) para evitar errores en Postgres estándar.
-- =============================================================================

DO $$
DECLARE
    role_name text;
    roles_list text[] := ARRAY['anon', 'authenticated'];
BEGIN
    FOREACH role_name IN ARRAY roles_list LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            -- Revoca accesos sobre todas las tablas del esquema público
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', role_name);
            -- Revoca accesos sobre todas las secuencias del esquema público
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', role_name);
            -- Revoca accesos sobre todas las funciones y procedimientos
            EXECUTE format('REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM %I', role_name);
            -- Revoca privilegios por defecto para futuras tablas
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', role_name);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', role_name);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM %I', role_name);
        END IF;
    END LOOP;
END $$;
