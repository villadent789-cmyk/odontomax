import {
  CalendarCheck,
  CalendarX,
  UserX,
  Clock,
  CheckCircle2,
} from 'lucide-react'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  PieChart,
  Pie,
  Cell,
} from 'recharts'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { Badge } from '@/design-system/components/Badge'
import { formatFecha } from '@/shared/format'
import type { ReporteCitasDTO } from './types'

interface CitasTabProps {
  citas?: ReporteCitasDTO
  cargando?: boolean
}

const COLORES_ESTADOS = [
  '#0D9488', // Teal
  '#0284C7', // Sky
  '#6366F1', // Indigo
  '#F59E0B', // Amber
  '#E11D48', // Rose
  '#94A3B8', // Slate
]

export function CitasTab({ citas, cargando }: CitasTabProps) {
  const datosPorDia = (citas?.citasPorDia ?? []).map((d) => ({
    fecha: formatFecha(d.fecha),
    atendidas: d.atendidas,
    programadas: d.programadas,
    canceladas: d.canceladas,
    noAsistio: d.noAsistio,
  }))

  const datosPie = (citas?.citasPorEstado ?? []).map((e, index) => ({
    name: e.estado,
    value: e.cantidad,
    porcentaje: e.porcentaje,
    color: COLORES_ESTADOS[index % COLORES_ESTADOS.length],
  }))

  const tasaAsistencia = citas?.tasaAsistencia ?? 0
  const colorTasa =
    tasaAsistencia >= 80 ? 'text-teal-600' : tasaAsistencia >= 60 ? 'text-amber-600' : 'text-rose-600'

  return (
    <div className="flex flex-col gap-6">
      {/* 1. KPIs de Agenda y Citas */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
        <StatCard
          etiqueta="Total Citas Agendadas"
          valor={(citas?.totalCitas ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Clock className="text-teal-600" />}
          pie="Total citas registradas en el periodo"
        />

        <StatCard
          etiqueta="Citas Atendidas con Éxito"
          valor={(citas?.atendidas ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<CheckCircle2 className="text-emerald-600" />}
          insignia={
            <Badge tono="primario">
              {tasaAsistencia}% Efectividad
            </Badge>
          }
          pie="Pacientes que completaron atención"
        />

        <StatCard
          etiqueta="Confirmadas y Programadas"
          valor={((citas?.confirmadas ?? 0) + (citas?.programadas ?? 0)).toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<CalendarCheck className="text-sky-600" />}
          pie={`Confirmadas: ${citas?.confirmadas ?? 0} · Por atender: ${citas?.programadas ?? 0}`}
        />

        <StatCard
          etiqueta="Inasistencias (No Asistió)"
          valor={(citas?.noAsistio ?? 0).toString()}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<UserX className="text-amber-600" />}
          pie={`Reprogramadas: ${citas?.reprogramadas ?? 0}`}
        />

        <StatCard
          etiqueta="Canceladas"
          valor={(citas?.canceladas ?? 0).toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<CalendarX className="text-rose-600" />}
          insignia={
            (citas?.canceladas ?? 0) > 0 ? (
              <Badge tono="peligro">
                Canceladas
              </Badge>
            ) : undefined
          }
          pie="Citas canceladas por paciente o clínica"
        />
      </div>

      {/* 2. Gráficos de Citas */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Gráfico 1: Evolución Diaria */}
        <Card className="flex flex-col p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h3 className="font-display text-base font-semibold text-on-surface">
                Comportamiento Diario de Citas
              </h3>
              <p className="text-xs text-on-surface-variant">
                Comparativa de atenciones, cancelaciones y ausencias por fecha
              </p>
            </div>
            <div className="flex items-center gap-3 text-xs">
              <span className="flex items-center gap-1 font-medium text-teal-600">
                <span className="size-2 rounded-full bg-teal-600" /> Atendidas
              </span>
              <span className="flex items-center gap-1 font-medium text-amber-500">
                <span className="size-2 rounded-full bg-amber-500" /> No asistió
              </span>
              <span className="flex items-center gap-1 font-medium text-rose-500">
                <span className="size-2 rounded-full bg-rose-500" /> Canceladas
              </span>
            </div>
          </div>

          <div className="h-72 w-full">
            {datosPorDia.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay citas en este rango de fechas.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={datosPorDia} margin={{ top: 10, right: 10, left: -15, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="fecha" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 10 }} />
                  <Tooltip
                    contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                  />
                  <Bar dataKey="atendidas" name="Atendidas" fill="#0D9488" stackId="a" radius={[0, 0, 0, 0]} />
                  <Bar dataKey="noAsistio" name="No asistió" fill="#F59E0B" stackId="a" radius={[0, 0, 0, 0]} />
                  <Bar dataKey="canceladas" name="Canceladas" fill="#E11D48" stackId="a" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>

        {/* Gráfico 2: Desglose y Tasa de Asistencia */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Proporción por Estados
            </h3>
            <p className="text-xs text-on-surface-variant">
              Porcentaje relativo de la agenda
            </p>
          </div>

          <div className="h-56 w-full">
            {datosPie.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                Sin registros.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={datosPie}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={75}
                    paddingAngle={3}
                  >
                    {datosPie.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(val, name, item) => [
                      `${val} citas (${item.payload.porcentaje}%)`,
                      name,
                    ]}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>

          <div className="mt-auto border-t border-outline-variant/20 pt-3 text-center">
            <span className="text-xs text-on-surface-variant">Tasa de Asistencia Global:</span>
            <p className={`font-display text-2xl font-bold ${colorTasa}`}>
              {tasaAsistencia}%
            </p>
            <p className="text-xs text-on-surface-variant">
              {tasaAsistencia >= 80
                ? 'Excelente índice de cumplimiento de agenda.'
                : 'Se sugiere activar confirmación por WhatsApp más temprana.'}
            </p>
          </div>
        </Card>
      </div>
    </div>
  )
}
