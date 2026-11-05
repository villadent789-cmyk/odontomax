-- Regularización de pagos de tratamientos (ej. ortodoncia) iniciados antes de la implementación
-- del sistema, para evitar registrar dinero falso en la caja del día al sólo regularizar saldos.
ALTER TABLE tratamientos
    ADD COLUMN IF NOT EXISTS pagado_antes_sistema NUMERIC(10,2) NOT NULL DEFAULT 0.00;
