import { Stethoscope, DollarSign } from 'lucide-react'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from 'recharts'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { Badge } from '@/design-system/components/Badge'
import { formatSoles } from '@/shared/format'
import type { ReporteDoctoresDTO } from './types'

interface DoctoresTabProps {
  doctores?: ReporteDoctoresDTO
  cargando?: boolean
}

/**
 * Productividad por doctor del periodo elegido. La meta mensual y la comisión de cada doctor se configuran en
 * Administración y se ven en la pestaña "Rendimiento"; aquí no hay metas ni comisiones fijas.
 */
export function DoctoresTab({ doctores, cargando }: DoctoresTabProps) {
  const lista = doctores?.doctores ?? []

  const datosGrafico = lista.map((d) => ({
    nombre: d.nombre.replace(/^Dr\.\s*|^Dra\.\s*/i, ''),
    ingresos: d.ingresosGenerados,
    atendidas: d.citasAtendidas,
  }))

  const totalIngresos = lista.reduce((acc, d) => acc + d.ingresosGenerados, 0)

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StatCard
          etiqueta="Doctores con Actividad"
          valor={lista.length.toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Stethoscope className="text-teal-600" />}
          pie="Odontólogos con citas o tratamientos en el periodo"
        />

        <StatCard
          etiqueta="Facturación Odontólogos"
          valor={formatSoles(totalIngresos)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<DollarSign className="text-emerald-600" />}
          pie="Total facturado en tratamientos atendidos"
        />
      </div>

      <Card className="flex flex-col p-5">
        <div className="mb-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div>
            <h3 className="font-display text-base font-semibold text-on-surface">
              Facturación Generada por Especialista
            </h3>
            <p className="text-xs text-on-surface-variant">Volumen de ingresos producidos en el periodo</p>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="inline-flex items-center gap-1.5 text-zinc-600 dark:text-zinc-400">
              <span className="inline-block size-2.5 rounded-xs bg-[#0D9488]" />
              Facturado
            </span>
          </div>
        </div>

        <div className="h-64 w-full">
          {datosGrafico.length === 0 ? (
            <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
              No hay datos registrados de doctores en el rango seleccionado.
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={datosGrafico} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                <XAxis dataKey="nombre" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `S/${v}`} />
                <Tooltip
                  formatter={(val) => [formatSoles(Number(val ?? 0)), 'Ingresos Generados']}
                  contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                />
                <Bar dataKey="ingresos" fill="#0D9488" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </Card>

      <Card className="p-5">
        <div className="mb-4">
          <h3 className="font-display text-base font-semibold text-on-surface">
            Rendimiento y Productividad Odontológica
          </h3>
          <p className="text-xs text-on-surface-variant">Consultas atendidas, tratamientos, facturación y efectividad</p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-on-surface">
            <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
              <tr>
                <th className="py-2.5 px-3">Especialista</th>
                <th className="py-2.5 px-3 text-center">Consultas Atendidas</th>
                <th className="py-2.5 px-3 text-center">Tratamientos</th>
                <th className="py-2.5 px-3 text-right">Facturado</th>
                <th className="py-2.5 px-3 text-center">Efectividad</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20">
              {lista.map((d) => (
                <tr key={d.doctorId} className="hover:bg-surface-container/50 transition-colors">
                  <td className="py-2.5 px-3 font-medium text-on-surface">
                    <div className="flex items-center gap-2">
                      <div className="flex size-7 items-center justify-center rounded-full bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300 font-bold text-xs">
                        DR
                      </div>
                      <div>
                        <p className="font-semibold">{d.nombre}</p>
                        <p className="text-xs text-on-surface-variant">{d.especialidad}</p>
                      </div>
                    </div>
                  </td>
                  <td className="py-2.5 px-3 text-center font-bold text-teal-700 dark:text-teal-300">
                    {d.citasAtendidas}
                  </td>
                  <td className="py-2.5 px-3 text-center text-on-surface-variant">{d.tratamientosRealizados}</td>
                  <td className="py-2.5 px-3 text-right font-bold text-on-surface">
                    {formatSoles(d.ingresosGenerados)}
                  </td>
                  <td className="py-2.5 px-3 text-center">
                    <Badge tono={d.tasaEfectividad >= 80 ? 'primario' : 'accion'}>{d.tasaEfectividad}%</Badge>
                  </td>
                </tr>
              ))}
              {lista.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-xs text-on-surface-variant">
                    No se encontraron registros de especialistas en este rango.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
