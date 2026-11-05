-- Laboratorio con un flujo corto: el trabajo se envía, vuelve para pruebas con el paciente (prueba 1, 2, 3…) y se
-- finaliza cuando queda correcto. Los siete estados anteriores se reducen a cuatro.
ALTER TABLE ordenes_laboratorio DROP CONSTRAINT IF EXISTS chk_orden_laboratorio_estado;

UPDATE ordenes_laboratorio SET estado = CASE estado
    WHEN 'SOLICITADO' THEN 'ENVIADO'
    WHEN 'EN_LABORATORIO' THEN 'ENVIADO'
    WHEN 'AJUSTE_REQUERIDO' THEN 'ENVIADO'
    WHEN 'EN_PRUEBA_CLINICA' THEN 'EN_PRUEBA'
    WHEN 'RECIBIDO_CLINICA' THEN 'EN_PRUEBA'
    WHEN 'INSTALADO_ENTREGADO' THEN 'FINALIZADO'
    ELSE estado END;

UPDATE ordenes_laboratorio_historial SET
    estado_anterior = CASE estado_anterior
        WHEN 'SOLICITADO' THEN 'ENVIADO'
        WHEN 'EN_LABORATORIO' THEN 'ENVIADO'
        WHEN 'AJUSTE_REQUERIDO' THEN 'ENVIADO'
        WHEN 'EN_PRUEBA_CLINICA' THEN 'EN_PRUEBA'
        WHEN 'RECIBIDO_CLINICA' THEN 'EN_PRUEBA'
        WHEN 'INSTALADO_ENTREGADO' THEN 'FINALIZADO'
        ELSE estado_anterior END,
    estado_nuevo = CASE estado_nuevo
        WHEN 'SOLICITADO' THEN 'ENVIADO'
        WHEN 'EN_LABORATORIO' THEN 'ENVIADO'
        WHEN 'AJUSTE_REQUERIDO' THEN 'ENVIADO'
        WHEN 'EN_PRUEBA_CLINICA' THEN 'EN_PRUEBA'
        WHEN 'RECIBIDO_CLINICA' THEN 'EN_PRUEBA'
        WHEN 'INSTALADO_ENTREGADO' THEN 'FINALIZADO'
        ELSE estado_nuevo END;

ALTER TABLE ordenes_laboratorio ADD CONSTRAINT chk_orden_laboratorio_estado
    CHECK (estado IN ('ENVIADO', 'EN_PRUEBA', 'FINALIZADO', 'CANCELADO'));
ALTER TABLE ordenes_laboratorio ALTER COLUMN estado SET DEFAULT 'ENVIADO';

CREATE TABLE IF NOT EXISTS ordenes_laboratorio_pruebas (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id uuid NOT NULL REFERENCES ordenes_laboratorio(id) ON DELETE CASCADE,
    numero integer NOT NULL CHECK (numero >= 1),
    fecha date NOT NULL DEFAULT CURRENT_DATE,
    resultado varchar(20) NOT NULL CHECK (resultado IN ('NECESITA_AJUSTE', 'CORRECTO')),
    notas varchar(500),
    registrado_por varchar(120),
    creado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_orden_laboratorio_prueba UNIQUE (orden_id, numero)
);
CREATE INDEX IF NOT EXISTS idx_ordenes_lab_pruebas_orden ON ordenes_laboratorio_pruebas(orden_id);
