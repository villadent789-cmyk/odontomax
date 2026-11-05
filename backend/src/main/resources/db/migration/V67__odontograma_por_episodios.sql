-- Odontograma por episodios: cada vez que el paciente llega (dolor, tratamiento, control a los 3 o 4 meses) se abre un
-- episodio con su odontograma INICIAL, y al terminar (alta odontológica) se cierra con el FINAL. Cada captura es una
-- fotografía fija del estado de todas las piezas en ese momento, con su fecha, para comparar cómo llegó y cómo salió.
CREATE TABLE IF NOT EXISTS odontograma_episodios (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id uuid NOT NULL REFERENCES pacientes(id) ON DELETE CASCADE,
    numero integer NOT NULL CHECK (numero >= 1),
    motivo varchar(300),
    estado varchar(10) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'CERRADO')),
    inicial_en timestamptz NOT NULL DEFAULT now(),
    piezas_iniciales jsonb NOT NULL DEFAULT '[]'::jsonb,
    inicial_por varchar(120),
    final_en timestamptz,
    piezas_finales jsonb,
    final_por varchar(120),
    nota_final varchar(500),
    CONSTRAINT uq_odontograma_episodio UNIQUE (paciente_id, numero)
);
CREATE INDEX IF NOT EXISTS idx_odontograma_episodios_paciente ON odontograma_episodios(paciente_id, numero DESC);
-- Un paciente tiene a lo sumo un episodio abierto a la vez.
CREATE UNIQUE INDEX IF NOT EXISTS uq_odontograma_episodio_abierto ON odontograma_episodios(paciente_id) WHERE estado = 'ABIERTO';
