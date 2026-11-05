-- =============================================================================
-- V17 · Adelantos Yape/Efectivo, Tareas WhatsApp, Saldos a Favor y Outbox
-- Conforme a docs/especificaciones-v2.md (Secciones 4 y 5):
-- 1. Adelantos obligatorios en Villadent (Yape y Efectivo).
-- 2. Estados nuevos: PENDIENTE_DE_PAGO, CONFIRMADA_CON_ADELANTO, EXPIRADA.
-- 3. Configuración de negocio parametrizable por sede.
-- 4. Registro y control riguroso de contactos WhatsApp y llamadas telefónicas.
-- 5. Saldos a favor de pacientes por cancelaciones anticipadas (>= 24 horas).
-- 6. Tabla event_publication para outbox de Spring Modulith.
-- =============================================================================

-- 1. Parámetros de negocio configurables por sede
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS monto_adelanto_defecto numeric(10,2) NOT NULL DEFAULT 30.00;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS horas_expiracion_adelanto integer NOT NULL DEFAULT 2;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS horas_anticipacion_cancelacion integer NOT NULL DEFAULT 24;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS horas_recordatorio_whatsapp integer NOT NULL DEFAULT 24;
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS hora_inicio_llamadas time NOT NULL DEFAULT '08:30';
ALTER TABLE sedes ADD COLUMN IF NOT EXISTS dias_vigencia_promocion_cumpleanos integer NOT NULL DEFAULT 7;

-- Villamax no cobra adelanto según Sección 4 de especificaciones
UPDATE sedes
SET monto_adelanto_defecto = 0.00
WHERE unidad_de_negocio = 'VILLAMAX' OR id = 'sede-villamax-chilca';

-- 2. Actualizar estados permitidos en citas
ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_estado_check;
ALTER TABLE citas ADD CONSTRAINT citas_estado_check CHECK (
    estado IN (
        'PENDIENTE_WEB',
        'PENDIENTE_DE_PAGO',
        'CONFIRMADA_CON_ADELANTO',
        'PROGRAMADA',
        'CONFIRMADA',
        'EN_SALA',
        'ATENDIDA',
        'CANCELADA',
        'EXPIRADA',
        'NO_ASISTIO'
    )
);

-- Actualizar exclusion constraints para liberar turnos si la cita expira o se cancela
ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_sin_choque_doctor;
ALTER TABLE citas ADD CONSTRAINT citas_sin_choque_doctor EXCLUDE USING gist (
    doctor_id WITH =,
    rango_cita(fecha, hora, duracion_minutos) WITH &&
) WHERE (doctor_id IS NOT NULL AND estado NOT IN ('CANCELADA', 'NO_ASISTIO', 'EXPIRADA'));

ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_sin_choque_sillon;
ALTER TABLE citas ADD CONSTRAINT citas_sin_choque_sillon EXCLUDE USING gist (
    sede_id WITH =,
    sillon_numero WITH =,
    rango_cita(fecha, hora, duracion_minutos) WITH &&
) WHERE (sillon_numero IS NOT NULL AND estado NOT IN ('CANCELADA', 'NO_ASISTIO', 'EXPIRADA'));

-- 3. Columnas de gestión de adelanto en citas
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_requerido boolean NOT NULL DEFAULT false;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_monto numeric(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_estado text NOT NULL DEFAULT 'NO_APLICA';
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_medio_pago text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_numero_operacion text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_comprobante_url text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_registrado_por text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_registrado_en timestamptz;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS adelanto_limite_pago timestamptz;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_citas_adelanto_estado'
    ) THEN
        ALTER TABLE citas ADD CONSTRAINT chk_citas_adelanto_estado
            CHECK (adelanto_estado IN ('NO_APLICA','PENDIENTE','PAGADO','EXONERADO','SALDO_FAVOR','PERDIDO'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_citas_adelanto_medio_pago'
    ) THEN
        ALTER TABLE citas ADD CONSTRAINT chk_citas_adelanto_medio_pago
            CHECK (adelanto_medio_pago IS NULL OR adelanto_medio_pago IN ('YAPE','EFECTIVO'));
    END IF;
END $$;

-- 4. Columnas de control de confirmación y contacto en citas
ALTER TABLE citas ADD COLUMN IF NOT EXISTS estado_confirmacion text NOT NULL DEFAULT 'SIN_CONTACTAR';
ALTER TABLE citas ADD COLUMN IF NOT EXISTS ultimo_contacto_medio text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS ultimo_contacto_fecha timestamptz;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS ultimo_contacto_usuario text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS ultimo_contacto_resultado text;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_citas_estado_confirmacion'
    ) THEN
        ALTER TABLE citas ADD CONSTRAINT chk_citas_estado_confirmacion
            CHECK (estado_confirmacion IN ('SIN_CONTACTAR','MENSAJE_ENVIADO','CONFIRMADA','NO_RESPONDE','CANCELADA_POR_PACIENTE'));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_citas_contacto_medio'
    ) THEN
        ALTER TABLE citas ADD CONSTRAINT chk_citas_contacto_medio
            CHECK (ultimo_contacto_medio IS NULL OR ultimo_contacto_medio IN ('WHATSAPP','LLAMADA'));
    END IF;
END $$;

-- Índices para optimizar la bandeja del día y citas por expirar
CREATE INDEX IF NOT EXISTS idx_citas_adelanto_limite
    ON citas (adelanto_estado, adelanto_limite_pago)
    WHERE adelanto_estado = 'PENDIENTE';

CREATE INDEX IF NOT EXISTS idx_citas_confirmacion_dia
    ON citas (sede_id, fecha, estado_confirmacion);

-- 5. Historial detallado de contactos con el paciente (llamadas y WhatsApp)
CREATE TABLE IF NOT EXISTS cita_contactos (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cita_id         uuid NOT NULL REFERENCES citas(id) ON DELETE CASCADE,
    medio           text NOT NULL CHECK (medio IN ('WHATSAPP','LLAMADA')),
    resultado       text NOT NULL CHECK (resultado IN ('CONFIRMO','PIDIO_REPROGRAMAR','CANCELO','NO_RESPONDE','MENSAJE_ENVIADO')),
    notas           text,
    usuario         text NOT NULL,
    creado_en       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cita_contactos_cita
    ON cita_contactos (cita_id, creado_en DESC);

-- 6. Saldos a favor del paciente por cancelación anticipada (>= 24h)
CREATE TABLE IF NOT EXISTS paciente_saldos_favor (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id     uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    cita_origen_id  uuid REFERENCES citas(id) ON DELETE SET NULL,
    monto           numeric(10,2) NOT NULL CHECK (monto > 0),
    motivo          text NOT NULL,
    estado          text NOT NULL DEFAULT 'DISPONIBLE' CHECK (estado IN ('DISPONIBLE','APLICADO','ANULADO')),
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_paciente_saldos_paciente
    ON paciente_saldos_favor (paciente_id, estado);

-- 7. Outbox de Spring Modulith para publicación asíncrona de eventos de dominio
CREATE TABLE IF NOT EXISTS event_publication (
    id                  uuid PRIMARY KEY,
    listener_id         varchar(512) NOT NULL,
    event_type          varchar(512) NOT NULL,
    serialized_event    text NOT NULL,
    publication_date    timestamptz NOT NULL,
    completion_date     timestamptz
);

CREATE INDEX IF NOT EXISTS idx_event_publication_listener
    ON event_publication (listener_id, serialized_event);

CREATE INDEX IF NOT EXISTS idx_event_publication_completion
    ON event_publication (completion_date);
