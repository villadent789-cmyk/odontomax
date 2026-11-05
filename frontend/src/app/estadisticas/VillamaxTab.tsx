import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Banknote, Handshake, ScanLine, Wallet } from 'lucide-react'
import { Badge } from '@/design-system/components/Badge'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { buttonVariants } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatSoles, hoyEnLima } from '@/shared/format'
import { useCajaActiva } from '../caja/api-caja'
import { listarComisiones } from '../radiologia/api-radiologia'
import type { ReporteFinancieroDTO } from './types'

interface VillamaxTabProps {
  financiero?: ReporteFinancieroDTO
  /** Periodo del reporte (aaaa-mm-dd), el mismo del resto de pestañas. */
  desde: string
  hasta: string
  cargando?: boolean
}

interface FilaDerivador {
  nombre: string
  estudios: number
  generada: number
  pendiente: number
  pagada: number
}

/**
 * Resumen de Villamax para los dueños. Los ingresos, egresos y estudios salen del reporte financiero que ya consulta la
 * página (el servidor lo acota al negocio activo); aquí solo se suman las comisiones de quienes derivan pacientes.
 */
export function VillamaxTab({ financiero, desde, hasta, cargando }: VillamaxTabProps) {
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId ?? undefined
  // La comisión de quienes derivan solo la ve el administrador general.
  const verComisiones = sesion?.usuario.rol === 'ADMIN_GENERAL'
  const comisiones = useQuery({
    queryKey: ['radiologia', 'comisiones', 'todas'],
    queryFn: () => listarComisiones(),
    enabled: verComisiones,
  })
  const caja = useCajaActiva(sedeId)

  const todas = comisiones.data ?? []
  const delPeriodo = todas.filter((c) => {
    const dia = hoyEnLima(new Date(c.fechaEmision))
    return dia >= desde && dia <= hasta
  })
  const porPagar = todas.filter((c) => c.estado === 'PENDIENTE').reduce((suma, c) => suma + c.montoComision, 0)
  const generada = delPeriodo.reduce((suma, c) => suma + c.montoComision, 0)

  const derivadores = new Map<string, FilaDerivador>()
  for (const c of delPeriodo) {
    const fila = derivadores.get(c.doctorDerivadorId) ??
      { nombre: c.doctorDerivadorNombre, estudios: 0, generada: 0, pendiente: 0, pagada: 0 }
    fila.estudios += 1
    fila.generada += c.montoComision
    if (c.estado === 'PENDIENTE') fila.pendiente += c.montoComision
    if (c.estado === 'LIQUIDADO') fila.pagada += c.montoComision
    derivadores.set(c.doctorDerivadorId, fila)
  }
  const ranking = [...derivadores.values()].sort((a, b) => b.estudios - a.estudios)
  const estudios = financiero?.ingresosPorTratamiento ?? []
  const totalEstudios = estudios.reduce((suma, e) => suma + e.cantidadRealizados, 0)

  return (
    <div className="flex flex-col gap-6">
      <div className={`grid grid-cols-1 gap-4 sm:grid-cols-2 ${verComisiones ? 'xl:grid-cols-4' : ''}`}>
        <StatCard
          etiqueta="Estudios realizados"
          valor={String(totalEstudios)}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<ScanLine className="text-indigo-600" />}
          pie="Cobrados en el periodo"
        />
        <StatCard
          etiqueta="Ingresos"
          valor={formatSoles(financiero?.totalIngresos ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Banknote className="text-emerald-600" />}
          pie={`Egresos ${formatSoles(financiero?.totalGastos ?? 0)} · balance ${formatSoles(financiero?.balanceNeto ?? 0)}`}
        />
        {verComisiones && (<>
        <StatCard
          etiqueta="Comisiones del periodo"
          valor={formatSoles(generada)}
          cargando={comisiones.isPending}
          tonoIcono="accion"
          icono={<Handshake className="text-amber-600" />}
          pie={`${delPeriodo.length} derivaciones`}
        />
        <StatCard
          etiqueta="Por pagar a doctores"
          valor={formatSoles(porPagar)}
          cargando={comisiones.isPending}
          tonoIcono="accion"
          icono={<Wallet className="text-sky-600" />}
          pie={caja.data ? 'Caja de Villamax abierta' : 'Caja de Villamax cerrada o sin abrir'}
        />
        </>)}
      </div>

      <Card className="p-5">
        <h3 className="mb-3 font-display text-base font-semibold text-on-surface">Ingresos por estudio</h3>
        <table className="w-full text-left text-xs text-on-surface">
          <thead className="border-b border-outline-variant/30 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
            <tr>
              <th className="px-3 py-2">Estudio</th>
              <th className="px-3 py-2 text-center">Realizados</th>
              <th className="px-3 py-2 text-right">Ingresos</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/20">
            {estudios.map((e) => (
              <tr key={e.procedimientoNombre}>
                <td className="px-3 py-2 font-semibold">{e.procedimientoNombre}</td>
                <td className="px-3 py-2 text-center">{e.cantidadRealizados}</td>
                <td className="px-3 py-2 text-right font-bold">{formatSoles(e.totalIngresos)}</td>
              </tr>
            ))}
            {estudios.length === 0 && (
              <tr>
                <td colSpan={3} className="px-3 py-4 text-center text-on-surface-variant">Sin estudios cobrados en este periodo.</td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>

      {verComisiones && (
      <Card className="p-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-display text-base font-semibold text-on-surface">Doctores que derivan</h3>
          <Link to="/app/radiologia" className={buttonVariants({ variant: 'secundario', size: 'sm' })}>
            Ver y liquidar comisiones
          </Link>
        </div>
        <table className="w-full text-left text-xs text-on-surface">
          <thead className="border-b border-outline-variant/30 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
            <tr>
              <th className="px-3 py-2">Doctor</th>
              <th className="px-3 py-2 text-center">Pacientes enviados</th>
              <th className="px-3 py-2 text-right">Comisión</th>
              <th className="px-3 py-2 text-right">Pendiente</th>
              <th className="px-3 py-2 text-right">Pagada</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/20">
            {ranking.map((d) => (
              <tr key={d.nombre}>
                <td className="px-3 py-2 font-semibold">{d.nombre}</td>
                <td className="px-3 py-2 text-center"><Badge tono="primario">{d.estudios}</Badge></td>
                <td className="px-3 py-2 text-right">{formatSoles(d.generada)}</td>
                <td className="px-3 py-2 text-right">{formatSoles(d.pendiente)}</td>
                <td className="px-3 py-2 text-right">{formatSoles(d.pagada)}</td>
              </tr>
            ))}
            {ranking.length === 0 && (
              <tr>
                <td colSpan={5} className="px-3 py-4 text-center text-on-surface-variant">Ningún doctor derivó pacientes en este periodo.</td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>
      )}
    </div>
  )
}
