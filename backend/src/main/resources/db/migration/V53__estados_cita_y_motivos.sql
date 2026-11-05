-- V53: Regla única de estados de cita y trazabilidad de motivos

-- 1. Actualizar estados permitidos en citas (incorporando EN_ATENCION)
ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_estado_check;
ALTER TABLE citas ADD CONSTRAINT citas_estado_check CHECK (
    estado IN (
        'PENDIENTE_WEB',
        'PENDIENTE_DE_PAGO',
        'CONFIRMADA_CON_ADELANTO',
        'PROGRAMADA',
        'CONFIRMADA',
        'EN_SALA',
        'EN_ATENCION',
        'ATENDIDA',
        'CANCELADA',
        'EXPIRADA',
        'NO_ASISTIO'
    )
);

-- 2. Trazabilidad de cancelaciones y reprogramaciones
ALTER TABLE citas ADD COLUMN IF NOT EXISTS motivo_cancelacion_por text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS cancelada_en timestamptz;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS motivo_reprogramacion_por text;
ALTER TABLE citas ADD COLUMN IF NOT EXISTS reprogramada_en timestamptz;
