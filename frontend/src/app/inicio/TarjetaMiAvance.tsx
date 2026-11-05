import { Target } from 'lucide-react'
import { mensajeError } from '@/shared/errores'
import { formatSoles } from '@/shared/format'
import { useMiAvance } from '../estadisticas/api-estadisticas'
import { Tarjeta } from './Tarjeta'

/** Mes actual (aaaa-mm) según la hora de Lima a partir del día de hoy ya calculado por la pantalla. */
function mesDe(hoy: string): string {
  return hoy.slice(0, 7)
}

function minutos(valor?: number | null): string {
  return valor == null ? '—' : `${valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })} min`
}

/** Avance del mes del odontólogo: lo cobrado frente a su meta y sus tiempos. Nunca muestra comisión. */
export function TarjetaMiAvance({ hoy }: { hoy: string }) {
  const consulta = useMiAvance(mesDe(hoy))
  const d = consulta.data
  const avance = d?.porcentajeAvance ?? null
  const ancho = avance == null ? 0 : Math.min(100, avance)

  return (
    <Tarjeta
      id="mi-avance"
      titulo="Tu avance del mes"
      icono={<Target aria-hidden />}
      cargando={consulta.isPending}
      error={consulta.isError ? mensajeError(consulta.error) : null}
      reintentar={() => void consulta.refetch()}
    >
      {d && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-on-surface">
            Llevas <strong>{formatSoles(d.cobradoParaMeta ?? d.cobrado)}</strong>
            {d.metaMensual != null && <span className="text-on-surface-variant"> de {formatSoles(d.metaMensual)}</span>}
          </p>
          {(d.laboratorioDescontado ?? 0) > 0 && (
            <p className="text-xs text-on-surface-variant">
              Sin laboratorio: se descontaron {formatSoles(d.laboratorioDescontado ?? 0)} (la meta es por mano de obra).
            </p>
          )}
          {d.metaMensual != null && (
            <>
              <div
                role="progressbar"
                aria-label="Avance de tu meta mensual"
                aria-valuenow={Math.round(ancho)}
                aria-valuemin={0}
                aria-valuemax={100}
                className="h-2 w-full overflow-hidden rounded-full bg-surface-container"
              >
                <div className="h-full rounded-full bg-primary" style={{ width: `${ancho}%` }} />
              </div>
              <p className="text-xs text-on-surface-variant">
                {avance?.toLocaleString('es-PE', { maximumFractionDigits: 1 })} % de tu meta
              </p>
            </>
          )}
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs">
            <div>
              <dt className="text-on-surface-variant">Citas atendidas</dt>
              <dd className="font-semibold text-on-surface">{d.citasAtendidas}</dd>
            </div>
            <div>
              <dt className="text-on-surface-variant">Duración promedio</dt>
              <dd className="font-semibold text-on-surface">
                {d.duracionRealPromedioMin == null
                  ? '—'
                  : `${minutos(d.duracionRealPromedioMin)} (prevista ${minutos(d.duracionPrevistaPromedioMin)})`}
              </dd>
            </div>
            <div>
              <dt className="text-on-surface-variant">Espera promedio</dt>
              <dd className="font-semibold text-on-surface">{minutos(d.esperaPromedioMin)}</dd>
            </div>
          </dl>
        </div>
      )}
    </Tarjeta>
  )
}
