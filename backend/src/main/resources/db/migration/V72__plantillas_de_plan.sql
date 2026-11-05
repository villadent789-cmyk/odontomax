-- Plantillas de plan de tratamiento: los planes que el doctor arma una y otra vez (un servicio del catálogo, cuántas piezas,
-- gravedad y sesiones). Al elegir una plantilla el plan queda precargado; el precio sale siempre del catálogo vigente.
CREATE TABLE IF NOT EXISTS plantillas_plan (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    unidad_de_negocio varchar(20) NOT NULL DEFAULT 'VILLADENT',
    nombre varchar(120) NOT NULL,
    procedimiento_id varchar(60) NOT NULL,
    servicio_nombre varchar(200) NOT NULL,
    piezas integer NOT NULL DEFAULT 1 CHECK (piezas >= 1),
    gravedad varchar(10) CHECK (gravedad IS NULL OR gravedad IN ('LEVE', 'MODERADO', 'SEVERO')),
    sesiones integer NOT NULL DEFAULT 1 CHECK (sesiones >= 1),
    notas varchar(300),
    activo boolean NOT NULL DEFAULT true,
    creado_por varchar(120),
    creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_plantillas_plan_nombre ON plantillas_plan(unidad_de_negocio, lower(nombre)) WHERE activo;
