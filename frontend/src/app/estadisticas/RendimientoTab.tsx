import { useMemo, useState } from 'react'
import { Card } from '@/design-system/components/Card'
import { ErrorState } from '@/design-system/components/ErrorState'
import { Skeleton } from '@/design-system/components/Skeleton'
import { mensajeError } from '@/shared/errores'
import { formatSoles } from '@/shared/format'
import { useRendimientoDoctores } from './api-estadisticas'
import type { RendimientoDoctorDTO } from './types'

const JUNTAS = 'juntas'
const COMPARAR = 'comparar'

/** Mes actual en formato aaaa-mm según la hora de Lima. */
function mesActualEnLima(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit' })
    .format(new Date())
    .slice(0, 7)
}

function minutos(valor?: number | null): string {
  return valor == null ? '—' : `${valor.toLocaleString('es-PE', { maximumFractionDigits: 1 })} min`
}

/**
 * Rendimiento mensual de los doctores de Villadent: lo cobrado frente a su meta, tiempos de atención y, solo para el
 * administrador general, la comisión estimada sobre el excedente. Villamax no entra aquí.
 */
export function RendimientoTab() {
  const [mes, setMes] = useState(mesActualEnLima)
  const [vista, setVista] = useState<string>(JUNTAS)
  const sedeConsultada = vista === JUNTAS || vista === COMPARAR ? undefined : vista
  const consulta = useRendimientoDoctores(mes, sedeConsultada)
  const datos = consulta.data
  const sedes = useMemo(() => datos?.sedes ?? [], [datos])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end gap-4 print:hidden">
        <label className="flex flex-col gap-1 text-xs font-semibold text-on-surface-variant">
          Mes
          <input
            type="month"
            value={mes}
            max={mesActualEnLima()}
            onChange={(e) => e.target.value && setMes(e.target.value)}
            className="rounded-control border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
          />
        </label>
        <div role="group" aria-label="Vista del rendimiento" className="flex flex-wrap gap-1.5">
          {[
            ...(sedes.length > 1 || vista !== JUNTAS ? [{ id: JUNTAS, nombre: 'Todas las sedes juntas' }] : []),
            ...sedes.map((s) => ({ id: s.id, nombre: s.nombre })),
            ...(sedes.length > 1 ? [{ id: COMPARAR, nombre: 'Comparar' }] : []),
          ].map((opcion) => (
            <button
              key={opcion.id}
              type="button"
              aria-pressed={vista === opcion.id}
              onClick={() => setVista(opcion.id)}
              className={`rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
                vista === opcion.id
                  ? 'bg-acento text-on-acento shadow-sm'
                  : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
              }`}
            >
              {opcion.nombre}
            </button>
          ))}
        </div>
      </div>

      <p className="text-xs text-on-surface-variant">
        Un tiempo de atención largo no siempre es mala atención: la duración real se compara con la duración prevista de
        cada cita, no con el promedio de todos. Villamax no se incluye en estos números.
      </p>

      {consulta.isPending && <Skeleton className="h-48 w-full" />}
      {consulta.isError && (
        <ErrorState
          titulo="No se pudo cargar el rendimiento"
          descripcion={mensajeError(consulta.error)}
          onReintentar={() => void consulta.refetch()}
        />
      )}

      {datos && datos.doctores.length === 0 && (
        <Card className="p-5 text-sm text-on-surface-variant">No hay doctores con actividad en este mes.</Card>
      )}

      {datos && datos.doctores.length > 0 && vista === COMPARAR && (
        <TablaComparar doctores={datos.doctores} sedes={sedes} total={datos.totalCobrado} />
      )}

      {datos && datos.doctores.length > 0 && vista !== COMPARAR && (
        <>
          <p className="text-sm font-semibold text-on-surface">
            Cobrado en el mes: <span data-testid="total-cobrado">{formatSoles(datos.totalCobrado)}</span>
          </p>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {datos.doctores.map((d) => (
              <TarjetaDoctor key={d.doctorId} doctor={d} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function TablaComparar({
  doctores,
  sedes,
  total,
}: {
  doctores: RendimientoDoctorDTO[]
  sedes: { id: string; nombre: string }[]
  total: number
}) {
  return (
    <Card className="overflow-x-auto p-5">
      <table className="w-full text-left text-xs text-on-surface">
        <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
          <tr>
            <th className="px-3 py-2.5">Doctor</th>
            {sedes.map((s) => (
              <th key={s.id} className="px-3 py-2.5 text-right">
                {s.nombre}
              </th>
            ))}
            <th className="px-3 py-2.5 text-right">Total</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant/20">
          {doctores.map((d) => (
            <tr key={d.doctorId}>
              <td className="px-3 py-2.5 font-semibold">{d.nombre}</td>
              {sedes.map((s) => (
                <td key={s.id} className="px-3 py-2.5 text-right">
                  {formatSoles(d.cobradoPorSede[s.id] ?? 0)}
                </td>
              ))}
              <td className="px-3 py-2.5 text-right font-bold">{formatSoles(d.cobrado)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot className="border-t border-outline-variant/40 font-bold">
          <tr>
            <td className="px-3 py-2.5">Total</td>
            {sedes.map((s) => (
              <td key={s.id} className="px-3 py-2.5 text-right">
                {formatSoles(doctores.reduce((acc, d) => acc + (d.cobradoPorSede[s.id] ?? 0), 0))}
              </td>
            ))}
            <td className="px-3 py-2.5 text-right">{formatSoles(total)}</td>
          </tr>
        </tfoot>
      </table>
    </Card>
  )
}

function TarjetaDoctor({ doctor }: { doctor: RendimientoDoctorDTO }) {
  const avance = doctor.porcentajeAvance ?? null
  const ancho = avance == null ? 0 : Math.min(100, avance)
  const supero = avance != null && avance >= 100
  return (
    <Card className="flex flex-col gap-3 p-5" aria-label={`Rendimiento de ${doctor.nombre}`}>
      <h3 className="font-display text-base font-bold text-on-surface">{doctor.nombre}</h3>

      <div className="flex flex-col gap-1.5">
        <p className="text-sm text-on-surface">
          Cobrado <strong>{formatSoles(doctor.cobradoParaMeta ?? doctor.cobrado)}</strong>
          {doctor.metaMensual != null && <span className="text-on-surface-variant"> de {formatSoles(doctor.metaMensual)}</span>}
        </p>
        {(doctor.laboratorioDescontado ?? 0) > 0 && (
          <p className="text-xs text-on-surface-variant">
            Sin laboratorio: se descontaron {formatSoles(doctor.laboratorioDescontado ?? 0)} de trabajos de laboratorio (cobrado total {formatSoles(doctor.cobrado)}).
          </p>
        )}
        {doctor.metaMensual != null ? (
          <>
            <div
              role="progressbar"
              aria-label={`Avance de la meta de ${doctor.nombre}`}
              aria-valuenow={Math.round(ancho)}
              aria-valuemin={0}
              aria-valuemax={100}
              className="h-2 w-full overflow-hidden rounded-full bg-surface-container"
            >
              <div
                className={`h-full rounded-full ${supero ? 'bg-emerald-500' : 'bg-primary'}`}
                style={{ width: `${ancho}%` }}
              />
            </div>
            <p className="text-xs text-on-surface-variant">
              {avance?.toLocaleString('es-PE', { maximumFractionDigits: 1 })} % de la meta
              {supero ? ' · meta lograda' : ''}
            </p>
          </>
        ) : (
          <p className="text-xs text-on-surface-variant">Sin meta definida</p>
        )}
        {doctor.comisionEstimada != null && (
          <p className="text-xs font-semibold text-emerald-700 dark:text-emerald-300">
            Comisión estimada {formatSoles(doctor.comisionEstimada)}
            {doctor.comisionExcedentePorcentaje != null && ` (${doctor.comisionExcedentePorcentaje} % del excedente)`}
          </p>
        )}
      </div>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs">
        <Dato etiqueta="Citas atendidas" valor={String(doctor.citasAtendidas)} />
        <Dato
          etiqueta="Duración promedio"
          valor={
            doctor.duracionRealPromedioMin == null
              ? '—'
              : `${minutos(doctor.duracionRealPromedioMin)} (prevista ${minutos(doctor.duracionPrevistaPromedioMin)})`
          }
        />
        <Dato etiqueta="Espera promedio" valor={minutos(doctor.esperaPromedioMin)} />
        <Dato
          etiqueta="Pasaron del tiempo previsto"
          valor={
            doctor.porcentajeCitasQuePasaronDelTiempoPrevisto == null
              ? '—'
              : `${doctor.porcentajeCitasQuePasaronDelTiempoPrevisto} % de las citas`
          }
        />
        <Dato etiqueta="Canceladas" valor={String(doctor.citasCanceladas)} />
        <Dato etiqueta="No asistieron" valor={String(doctor.citasNoAsistio)} />
      </dl>
    </Card>
  )
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-on-surface-variant">{etiqueta}</dt>
      <dd className="font-semibold text-on-surface">{valor}</dd>
    </div>
  )
}
