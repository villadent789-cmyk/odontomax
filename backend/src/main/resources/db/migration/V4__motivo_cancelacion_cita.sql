-- =============================================================================
-- ODONTOMAX - MIGRACIÓN V4: Soporte para motivo en cambio de estado de cita
-- =============================================================================

ALTER TABLE citas ADD COLUMN IF NOT EXISTS motivo_cancelacion text;
