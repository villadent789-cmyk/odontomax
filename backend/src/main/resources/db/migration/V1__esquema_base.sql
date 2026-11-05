-- =============================================================================
-- V1 · Esquema base · OdontoMax (Centro Odontológico Multisede)
-- Motor: PostgreSQL 15+ (Supabase) / Flyway Migration
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -----------------------------------------------------------------------------
-- Utilidades y Funciones del Sistema
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_actualizado_en() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.actualizado_en := now();
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION generar_codigo_ticket() RETURNS text
LANGUAGE sql AS $$
    SELECT 'RV-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
$$;

CREATE OR REPLACE FUNCTION rango_cita(p_fecha date, p_hora time, p_duracion integer)
RETURNS tsrange IMMUTABLE LANGUAGE sql AS $$
    SELECT tsrange(
        (p_fecha + p_hora),
        (p_fecha + p_hora) + (p_duracion || ' minutes')::interval
    );
$$;

-- -----------------------------------------------------------------------------
-- 1. Sedes y Horarios
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sedes (
    id              text PRIMARY KEY,
    nombre          text NOT NULL,
    direccion       text NOT NULL,
    ciudad          text NOT NULL DEFAULT 'Huancayo',
    telefono        text,
    whatsapp        text,
    email           text,
    horario_texto   text,
    color           text DEFAULT '#C59B27',
    color_secundario text DEFAULT '#FEF3C7',
    sillones        smallint NOT NULL DEFAULT 1 CHECK (sillones > 0),
    descripcion     text,
    foto_portada    text,
    fotos           text[] NOT NULL DEFAULT '{}',
    latitud         numeric(9,6),
    longitud        numeric(9,6),
    activa          boolean NOT NULL DEFAULT true,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sede_horarios (
    id              smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sede_id         text NOT NULL REFERENCES sedes(id) ON DELETE CASCADE,
    dia_semana      smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_apertura   time NOT NULL,
    hora_cierre     time NOT NULL,
    CHECK (hora_cierre > hora_apertura),
    UNIQUE (sede_id, dia_semana)
);

-- -----------------------------------------------------------------------------
-- 2. Usuarios y Especialistas (RBAC)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuarios (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id    uuid UNIQUE,
    nombre          text NOT NULL,
    email           text NOT NULL,
    password_hash   text,
    rol             text NOT NULL CHECK (rol IN ('ADMIN_GENERAL','ADMIN_SEDE','ODONTOLOGO','RECEPCION')),
    sede_id         text REFERENCES sedes(id),
    activo          boolean NOT NULL DEFAULT true,
    ultimo_acceso   timestamptz,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuarios_rol_sede CHECK (
        (rol = 'ADMIN_GENERAL' AND sede_id IS NULL)
        OR (rol IN ('ADMIN_SEDE','RECEPCION') AND sede_id IS NOT NULL)
        OR rol = 'ODONTOLOGO'
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS usuarios_email_uk ON usuarios (lower(email));

CREATE TABLE IF NOT EXISTS especialistas (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid UNIQUE REFERENCES usuarios(id),
    nombre          text NOT NULL,
    especialidad    text NOT NULL,
    cop             text UNIQUE,
    email           text,
    telefono        text,
    foto_key        text,
    activo          boolean NOT NULL DEFAULT true,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS especialista_sedes (
    especialista_id uuid NOT NULL REFERENCES especialistas(id) ON DELETE CASCADE,
    sede_id         text NOT NULL REFERENCES sedes(id),
    PRIMARY KEY (especialista_id, sede_id)
);

-- Estructura alineada con EspecialistaHorarioEntity (id uuid, activo, creado_en).
-- V8 usa CREATE TABLE IF NOT EXISTS sobre esta misma tabla, por lo que la definición válida es esta.
CREATE TABLE IF NOT EXISTS especialista_horarios (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    especialista_id uuid NOT NULL,
    sede_id         text NOT NULL,
    dia_semana      smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio     time NOT NULL,
    hora_fin        time NOT NULL,
    activo          boolean NOT NULL DEFAULT true,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    CHECK (hora_fin > hora_inicio),
    UNIQUE (especialista_id, sede_id, dia_semana),
    FOREIGN KEY (especialista_id, sede_id) REFERENCES especialista_sedes (especialista_id, sede_id) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- 3. Catálogos Clínicos y Servicios Web
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorias_procedimiento (
    id              smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          text NOT NULL UNIQUE,
    orden           smallint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS catalogo_procedimientos (
    id                  text PRIMARY KEY,
    categoria_id        smallint NOT NULL REFERENCES categorias_procedimiento(id),
    nombre              text NOT NULL UNIQUE,
    descripcion         text,
    sesiones_sugeridas  smallint NOT NULL DEFAULT 1 CHECK (sesiones_sugeridas > 0),
    requiere_pieza      boolean NOT NULL DEFAULT false,
    precio_referencial  numeric(10,2) CHECK (precio_referencial IS NULL OR precio_referencial >= 0),
    activo              boolean NOT NULL DEFAULT true,
    orden               smallint NOT NULL DEFAULT 0,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS servicios_web (
    id              text PRIMARY KEY,
    nombre          text NOT NULL,
    especialidad    text NOT NULL,
    descripcion     text,
    duracion_min    integer NOT NULL CHECK (duracion_min > 0),
    precio_desde    numeric(10,2) CHECK (precio_desde IS NULL OR precio_desde >= 0),
    activo              boolean NOT NULL DEFAULT true,
    orden               smallint NOT NULL DEFAULT 0
);

-- -----------------------------------------------------------------------------
-- 4. Pacientes y Clínica (Compatible con Spring Boot)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pacientes (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_documento              text NOT NULL DEFAULT 'DNI' CHECK (tipo_documento IN ('DNI','CE','PASAPORTE','OTRO')),
    dni                         text NOT NULL,
    nombres                     text NOT NULL,
    apellidos                   text NOT NULL,
    fecha_nacimiento            date,
    sexo                        text,
    telefono                    text,
    email                       text,
    direccion                   text,
    contacto_emergencia_nombre  text,
    contacto_emergencia_telefono text,
    apoderado_nombre            text,
    apoderado_documento         text,
    apoderado_telefono          text,
    apoderado_parentesco        text,
    sede_id                     text NOT NULL REFERENCES sedes(id),
    fuente_captacion            text,
    alergias                    text,
    antecedentes_medicos        text,
    es_gestante                 boolean NOT NULL DEFAULT false,
    semanas_gestacion           integer CHECK (semanas_gestacion IS NULL OR semanas_gestacion BETWEEN 1 AND 45),
    notas                       text,
    saldo_pendiente             numeric(10,2) NOT NULL DEFAULT 0,
    consentimiento_datos_en     timestamptz,
    archivado                   boolean NOT NULL DEFAULT false,
    motivo_archivo              text,
    creado_en                   timestamptz NOT NULL DEFAULT now(),
    actualizado_en              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tipo_documento, dni)
);
CREATE INDEX IF NOT EXISTS pacientes_sede_idx ON pacientes (sede_id) WHERE NOT archivado;
CREATE INDEX IF NOT EXISTS pacientes_apellidos_idx ON pacientes (lower(apellidos), lower(nombres));

CREATE TABLE IF NOT EXISTS odontograma_registros (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id     uuid NOT NULL REFERENCES pacientes(id),
    pieza_fdi       integer NOT NULL CHECK (
                        (pieza_fdi / 10 BETWEEN 1 AND 4 AND pieza_fdi % 10 BETWEEN 1 AND 8)
                     OR (pieza_fdi / 10 BETWEEN 5 AND 8 AND pieza_fdi % 10 BETWEEN 1 AND 5)
                    ),
    estado          text NOT NULL,
    caras           text[] NOT NULL DEFAULT '{}',
    observacion     text,
    especialista_id uuid REFERENCES especialistas(id),
    registrado_por  uuid REFERENCES usuarios(id),
    registrado_en   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS odontograma_paciente_pieza_idx ON odontograma_registros (paciente_id, pieza_fdi, registrado_en DESC);

CREATE OR REPLACE VIEW v_odontograma_actual WITH (security_invoker = true) AS
SELECT DISTINCT ON (paciente_id, pieza_fdi)
       paciente_id, pieza_fdi, estado, caras, observacion, especialista_id, registrado_en
FROM odontograma_registros
ORDER BY paciente_id, pieza_fdi, registrado_en DESC, id;

CREATE TABLE IF NOT EXISTS tratamientos (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id         uuid NOT NULL REFERENCES pacientes(id),
    sede_id             text NOT NULL REFERENCES sedes(id),
    especialista_id     uuid REFERENCES especialistas(id),
    procedimiento_id    text REFERENCES catalogo_procedimientos(id),
    nombre              text NOT NULL,
    pieza_dental        text,
    diagnostico         text,
    modalidad_pago      text NOT NULL DEFAULT 'CONTADO' CHECK (modalidad_pago IN ('CONTADO','CUOTAS')),
    costo_total         numeric(10,2) NOT NULL CHECK (costo_total >= 0),
    estado              text NOT NULL DEFAULT 'PLANIFICADO' CHECK (estado IN ('PLANIFICADO','EN_PROCESO','COMPLETADO','CANCELADO')),
    fecha_inicio        date,
    fecha_fin_estimada  date,
    total_sesiones      smallint NOT NULL DEFAULT 1 CHECK (total_sesiones > 0),
    sesiones_realizadas smallint NOT NULL DEFAULT 0 CHECK (sesiones_realizadas >= 0),
    proximo_control     date,
    requiere_seguimiento boolean NOT NULL DEFAULT false,
    observaciones       text,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now(),
    CHECK (fecha_fin_estimada IS NULL OR fecha_inicio IS NULL OR fecha_fin_estimada >= fecha_inicio)
);
CREATE INDEX IF NOT EXISTS tratamientos_paciente_idx ON tratamientos (paciente_id);
CREATE INDEX IF NOT EXISTS tratamientos_control_idx ON tratamientos (proximo_control) WHERE estado = 'EN_PROCESO';

CREATE TABLE IF NOT EXISTS consentimientos (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id     uuid NOT NULL REFERENCES pacientes(id),
    tratamiento_id  uuid REFERENCES tratamientos(id),
    tipo            text NOT NULL CHECK (tipo IN ('TRATAMIENTO_DATOS','INFORMADO_PROCEDIMIENTO','USO_IMAGEN')),
    archivo_key     text,
    firmado_en      timestamptz NOT NULL DEFAULT now(),
    registrado_por  uuid REFERENCES usuarios(id)
);
CREATE INDEX IF NOT EXISTS consentimientos_paciente_idx ON consentimientos (paciente_id);

-- -----------------------------------------------------------------------------
-- 5. Citas y Anti-colisión (Compatible con Spring Boot)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS citas (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id         uuid NOT NULL REFERENCES pacientes(id),
    paciente_nombre     text,
    paciente_telefono   text,
    doctor_id           uuid REFERENCES especialistas(id),
    doctor_nombre       text,
    sede_id             text NOT NULL REFERENCES sedes(id),
    sillon_numero       integer CHECK (sillon_numero IS NULL OR sillon_numero > 0),
    servicio_id         text REFERENCES servicios_web(id),
    tratamiento_id      uuid REFERENCES tratamientos(id),
    motivo_consulta     text NOT NULL,
    fecha               date NOT NULL,
    hora                time NOT NULL,
    duracion_minutos    integer NOT NULL DEFAULT 30 CHECK (duracion_minutos > 0),
    estado              text NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN
                            ('PENDIENTE_WEB','PROGRAMADA','CONFIRMADA','EN_SALA','ATENDIDA','CANCELADA','NO_ASISTIO')),
    origen              text NOT NULL DEFAULT 'INTERNO' CHECK (origen IN ('WEB','INTERNO','WHATSAPP','TELEFONO')),
    notas               text,
    codigo_ticket       varchar(20),
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT citas_sin_choque_doctor EXCLUDE USING gist (
        doctor_id WITH =,
        rango_cita(fecha, hora, duracion_minutos) WITH &&
    ) WHERE (doctor_id IS NOT NULL AND estado NOT IN ('CANCELADA','NO_ASISTIO')),

    CONSTRAINT citas_sin_choque_sillon EXCLUDE USING gist (
        sede_id WITH =,
        sillon_numero WITH =,
        rango_cita(fecha, hora, duracion_minutos) WITH &&
    ) WHERE (sillon_numero IS NOT NULL AND estado NOT IN ('CANCELADA','NO_ASISTIO'))
);
CREATE UNIQUE INDEX IF NOT EXISTS citas_ticket_uk ON citas (codigo_ticket) WHERE codigo_ticket IS NOT NULL;
CREATE INDEX IF NOT EXISTS citas_agenda_idx ON citas (sede_id, fecha, hora);
CREATE INDEX IF NOT EXISTS citas_paciente_idx ON citas (paciente_id);

-- -----------------------------------------------------------------------------
-- 6. Finanzas (Estrictamente EFECTIVO y YAPE)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pagos (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sede_id             text NOT NULL REFERENCES sedes(id),
    paciente_id         uuid NOT NULL REFERENCES pacientes(id),
    tratamiento_id      uuid REFERENCES tratamientos(id),
    monto               numeric(10,2) NOT NULL CHECK (monto > 0),
    metodo_pago         text NOT NULL CHECK (metodo_pago IN ('EFECTIVO','YAPE')),
    numero_operacion    text,
    comprobante         text,
    pagado_en           timestamptz NOT NULL DEFAULT now(),
    registrado_por      uuid REFERENCES usuarios(id),
    anulado             boolean NOT NULL DEFAULT false,
    anulado_en          timestamptz,
    anulado_por         uuid REFERENCES usuarios(id),
    motivo_anulacion    text,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    CHECK (NOT anulado OR motivo_anulacion IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS pagos_comprobante_uk ON pagos (comprobante) WHERE comprobante IS NOT NULL AND NOT anulado;
CREATE INDEX IF NOT EXISTS pagos_sede_fecha_idx ON pagos (sede_id, pagado_en);
CREATE INDEX IF NOT EXISTS pagos_paciente_idx ON pagos (paciente_id);
CREATE INDEX IF NOT EXISTS pagos_tratamiento_idx ON pagos (tratamiento_id);

CREATE TABLE IF NOT EXISTS gastos (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sede_id             text NOT NULL REFERENCES sedes(id),
    categoria           text NOT NULL CHECK (categoria IN
                            ('INSUMOS_MATERIALES','SERVICIOS_BASICOS','MANTENIMIENTO','PLANILLA','ALQUILER','MARKETING','OTROS')),
    concepto            text NOT NULL,
    monto               numeric(10,2) NOT NULL CHECK (monto > 0),
    metodo_pago         text NOT NULL,
    comprobante         text,
    gastado_en          timestamptz NOT NULL DEFAULT now(),
    registrado_por      uuid REFERENCES usuarios(id),
    anulado             boolean NOT NULL DEFAULT false,
    motivo_anulacion    text,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    CHECK (NOT anulado OR motivo_anulacion IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS gastos_sede_fecha_idx ON gastos (sede_id, gastado_en);

CREATE TABLE IF NOT EXISTS caja_sesiones (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sede_id             text NOT NULL REFERENCES sedes(id),
    abierta_por         uuid NOT NULL REFERENCES usuarios(id),
    abierta_en          timestamptz NOT NULL DEFAULT now(),
    monto_apertura      numeric(10,2) NOT NULL DEFAULT 0 CHECK (monto_apertura >= 0),
    cerrada_por         uuid REFERENCES usuarios(id),
    cerrada_en          timestamptz,
    monto_esperado      numeric(10,2),
    monto_contado       numeric(10,2),
    diferencia          numeric(10,2) GENERATED ALWAYS AS (monto_contado - monto_esperado) STORED,
    observaciones       text,
    CHECK (cerrada_en IS NULL OR cerrada_en >= abierta_en)
);
CREATE UNIQUE INDEX IF NOT EXISTS caja_una_abierta_por_sede ON caja_sesiones (sede_id) WHERE cerrada_en IS NULL;

CREATE OR REPLACE VIEW v_tratamientos_saldo WITH (security_invoker = true) AS
SELECT t.*,
       COALESCE(p.pagado, 0)                    AS monto_pagado,
       t.costo_total - COALESCE(p.pagado, 0)    AS saldo_pendiente
FROM tratamientos t
LEFT JOIN LATERAL (
    SELECT sum(monto) AS pagado
    FROM pagos
    WHERE tratamiento_id = t.id AND NOT anulado
) p ON true;

CREATE OR REPLACE FUNCTION recalcular_saldo_paciente(p_paciente uuid) RETURNS void
LANGUAGE sql AS $$
    UPDATE pacientes
       SET saldo_pendiente =
             COALESCE((SELECT sum(costo_total) FROM tratamientos WHERE paciente_id = p_paciente AND estado <> 'CANCELADO'), 0)
           - COALESCE((SELECT sum(monto) FROM pagos WHERE paciente_id = p_paciente AND NOT anulado), 0)
     WHERE id = p_paciente;
$$;

CREATE OR REPLACE FUNCTION trg_recalcular_saldo() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM recalcular_saldo_paciente(NEW.paciente_id);
    IF TG_OP = 'UPDATE' AND OLD.paciente_id IS DISTINCT FROM NEW.paciente_id THEN
        PERFORM recalcular_saldo_paciente(OLD.paciente_id);
    END IF;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS pagos_saldo ON pagos;
CREATE TRIGGER pagos_saldo AFTER INSERT OR UPDATE ON pagos
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_saldo();

DROP TRIGGER IF EXISTS tratamientos_saldo ON tratamientos;
CREATE TRIGGER tratamientos_saldo AFTER INSERT OR UPDATE OF costo_total, estado, paciente_id ON tratamientos
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_saldo();

-- -----------------------------------------------------------------------------
-- 7. Inventario y Kardex Automatizado
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS productos_inventario (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo              text NOT NULL UNIQUE,
    nombre              text NOT NULL,
    categoria           text,
    unidad              text,
    precio_unitario     numeric(10,2) CHECK (precio_unitario IS NULL OR precio_unitario >= 0),
    stock_minimo        integer NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
    activo              boolean NOT NULL DEFAULT true,
    creado_en           timestamptz NOT NULL DEFAULT now(),
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inventario_stock (
    producto_id         uuid NOT NULL REFERENCES productos_inventario(id),
    sede_id             text NOT NULL REFERENCES sedes(id),
    cantidad            integer NOT NULL DEFAULT 0 CHECK (cantidad >= 0),
    PRIMARY KEY (producto_id, sede_id)
);

CREATE TABLE IF NOT EXISTS inventario_movimientos (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id         uuid NOT NULL REFERENCES productos_inventario(id),
    sede_id             text NOT NULL REFERENCES sedes(id),
    tipo                text NOT NULL CHECK (tipo IN
                            ('ENTRADA','SALIDA','AJUSTE','TRANSFERENCIA_ENTRADA','TRANSFERENCIA_SALIDA')),
    cantidad            integer NOT NULL CHECK (cantidad <> 0),
    lote                text,
    fecha_vencimiento   date,
    proveedor           text,
    motivo              text,
    registrado_por      uuid REFERENCES usuarios(id),
    creado_en           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS inv_mov_producto_idx ON inventario_movimientos (producto_id, sede_id, creado_en);
CREATE INDEX IF NOT EXISTS inv_mov_vencimiento_idx ON inventario_movimientos (fecha_vencimiento) WHERE fecha_vencimiento IS NOT NULL;

CREATE OR REPLACE FUNCTION trg_aplicar_movimiento() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO inventario_stock (producto_id, sede_id, cantidad)
    VALUES (NEW.producto_id, NEW.sede_id, 0)
    ON CONFLICT (producto_id, sede_id) DO NOTHING;

    UPDATE inventario_stock
       SET cantidad = cantidad + NEW.cantidad
     WHERE producto_id = NEW.producto_id AND sede_id = NEW.sede_id;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS inventario_movimiento_aplica ON inventario_movimientos;
CREATE TRIGGER inventario_movimiento_aplica AFTER INSERT ON inventario_movimientos
    FOR EACH ROW EXECUTE FUNCTION trg_aplicar_movimiento();

CREATE TABLE IF NOT EXISTS transferencias_inventario (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id         uuid NOT NULL REFERENCES productos_inventario(id),
    cantidad            integer NOT NULL CHECK (cantidad > 0),
    sede_origen_id      text NOT NULL REFERENCES sedes(id),
    sede_destino_id     text NOT NULL REFERENCES sedes(id),
    estado              text NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','EN_TRANSITO','RECIBIDO','CANCELADO')),
    motivo              text,
    solicitado_por      uuid REFERENCES usuarios(id),
    creado_en           timestamptz NOT NULL DEFAULT now(),
    recibido_en         timestamptz,
    CHECK (sede_origen_id <> sede_destino_id)
);

CREATE OR REPLACE FUNCTION trg_procesar_transferencia() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- Al iniciar transferencia (EN_TRANSITO): descuento de sede origen
    IF (TG_OP = 'INSERT' AND NEW.estado = 'EN_TRANSITO')
       OR (TG_OP = 'UPDATE' AND OLD.estado = 'PENDIENTE' AND NEW.estado = 'EN_TRANSITO') THEN
        INSERT INTO inventario_movimientos (producto_id, sede_id, tipo, cantidad, motivo, registrado_por)
        VALUES (NEW.producto_id, NEW.sede_origen_id, 'TRANSFERENCIA_SALIDA', -NEW.cantidad, 'Transferencia hacia ' || NEW.sede_destino_id, NEW.solicitado_por);
    END IF;

    -- Al recibir transferencia (RECIBIDO): suma a sede destino
    IF TG_OP = 'UPDATE' AND OLD.estado = 'EN_TRANSITO' AND NEW.estado = 'RECIBIDO' THEN
        NEW.recibido_en := now();
        INSERT INTO inventario_movimientos (producto_id, sede_id, tipo, cantidad, motivo, registrado_por)
        VALUES (NEW.producto_id, NEW.sede_destino_id, 'TRANSFERENCIA_ENTRADA', NEW.cantidad, 'Transferencia recibida desde ' || NEW.sede_origen_id, NEW.solicitado_por);
    END IF;

    -- Si se cancela mientras estaba en tránsito, se revierte a sede origen
    IF TG_OP = 'UPDATE' AND OLD.estado = 'EN_TRANSITO' AND NEW.estado = 'CANCELADO' THEN
        INSERT INTO inventario_movimientos (producto_id, sede_id, tipo, cantidad, motivo, registrado_por)
        VALUES (NEW.producto_id, NEW.sede_origen_id, 'AJUSTE', NEW.cantidad, 'Reversión por transferencia cancelada', NEW.solicitado_por);
    END IF;

    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS transferencias_procesar_trg ON transferencias_inventario;
CREATE TRIGGER transferencias_procesar_trg BEFORE INSERT OR UPDATE ON transferencias_inventario
    FOR EACH ROW EXECUTE FUNCTION trg_procesar_transferencia();

-- -----------------------------------------------------------------------------
-- 8. Radiografías y Archivos (Cloudflare R2 + Spring Boot)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS radiografias_estudios (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id                 uuid NOT NULL REFERENCES pacientes(id),
    doctor_id                   uuid REFERENCES especialistas(id),
    sede_id                     text REFERENCES sedes(id),
    tipo_estudio                text NOT NULL CHECK (tipo_estudio IN
                                    ('PANORAMICA','PERIAPICAL','ALETA_MORDIDA','CEFALOMETRICA','TOMOGRAFIA',
                                     'FOTO_CLINICA','FOTO_ANTES','FOTO_DESPUES','INFORME')),
    titulo                      text,
    pieza_fdi                   integer,
    etapa                       text NOT NULL DEFAULT 'INICIAL' CHECK (etapa IN ('INICIAL','CONTROL','FINAL')),
    archivo_url                 text NOT NULL,
    archivo_key                 text,
    nombre_archivo              varchar(255),
    mime_type                   text,
    tamano_bytes                bigint CHECK (tamano_bytes IS NULL OR tamano_bytes > 0),
    sha256                      char(64),
    diagnostico_radiografico    text,
    fecha_toma                  date NOT NULL DEFAULT CURRENT_DATE,
    eliminado_en                timestamptz,
    creado_en                   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS rx_paciente_idx ON radiografias_estudios (paciente_id) WHERE eliminado_en IS NULL;

-- -----------------------------------------------------------------------------
-- 9. Triggers Automáticos de Timestamp
-- -----------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['sedes','usuarios','especialistas','catalogo_procedimientos',
                             'pacientes','tratamientos','citas','productos_inventario']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS %I_actualizado ON %I', t, t);
        EXECUTE format('CREATE TRIGGER %I_actualizado BEFORE UPDATE ON %I
                        FOR EACH ROW EXECUTE FUNCTION set_actualizado_en()', t, t);
    END LOOP;
END $$;
