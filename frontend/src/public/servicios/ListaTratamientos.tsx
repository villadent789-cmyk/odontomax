import { TarjetaTratamiento } from './TarjetaTratamiento'
import { tratamientosPorGrupo } from './tratamientosGenerales'

/** Columnas de pantalla ancha según cuántas tarjetas tiene el grupo, para que cada fila quede completa. */
const COLUMNAS: Record<number, string> = {
  1: 'lg:grid-cols-1',
  2: 'lg:grid-cols-2',
  3: 'lg:grid-cols-3',
  4: 'lg:grid-cols-4',
}

/** Todos los tratamientos generales, en secciones con su título. Es la misma lista en el inicio y en Servicios. */
export function ListaTratamientos() {
  return (
    <div className="flex flex-col gap-12">
      {tratamientosPorGrupo().map(({ grupo, tratamientos }) => (
        <section key={grupo} aria-label={grupo} className="flex flex-col gap-5">
          <h2 className="border-b border-outline-variant/50 pb-2 font-display text-2xl font-bold text-primary">{grupo}</h2>
          <div className={`grid gap-6 sm:grid-cols-2 ${COLUMNAS[Math.min(tratamientos.length, 4)]}`}>
            {tratamientos.map((t) => (
              <TarjetaTratamiento key={t.id} tratamiento={t} />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
