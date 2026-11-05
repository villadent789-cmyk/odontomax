import {
  Users,
  UserPlus,
  UserCheck,
  Gift,
  MessageCircle,
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
import type { ReportePacientesDTO } from './types'

interface PacientesTabProps {
  pacientes?: ReportePacientesDTO
  cargando?: boolean
}

const COLORES_FUENTES = [
  '#0D9488', // Teal
  '#6366F1', // Indigo
  '#EC4899', // Pink (Instagram)
  '#0284C7', // Sky
  '#F59E0B', // Amber
  '#10B981', // Emerald
]

export function PacientesTab({ pacientes, cargando }: PacientesTabProps) {
  const datosMeses = (pacientes?.pacientesPorMes ?? []).map((m) => ({
    mes: m.mes,
    nuevos: m.nuevos,
    recurrentes: m.recurrentes,
  }))

  const datosFuentes = (pacientes?.fuentesCaptacion ?? []).map((f, index) => ({
    name: f.fuente,
    value: f.cantidad,
    porcentaje: f.porcentaje,
    color: COLORES_FUENTES[index % COLORES_FUENTES.length],
  }))

  const enviarFelicitacionWhatsApp = (telefono?: string | null, nombre?: string) => {
    if (!telefono) return
    const telLimpio = telefono.replace(/\D/g, '')
    const mensaje = encodeURIComponent(
      `¡Feliz Cumpleaños, ${nombre}! 🎉 En Clínica Odontológica OdontoMax queremos celebrar tu día especial. Presenta este mensaje en tu próxima cita y obtén una profilaxis o descuento especial en tu tratamiento. ¡Te deseamos un gran día!`,
    )
    window.open(`https://wa.me/51${telLimpio}?text=${mensaje}`, '_blank')
  }

  return (
    <div className="flex flex-col gap-6">
      {/* 1. KPIs de Pacientes */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          etiqueta="Total Pacientes en Base"
          valor={(pacientes?.totalPacientes ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Users className="text-teal-600" />}
          pie="Pacientes activos en el sistema"
        />

        <StatCard
          etiqueta="Nuevos Pacientes Registrados"
          valor={(pacientes?.pacientesNuevos ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<UserPlus className="text-emerald-600" />}
          insignia={
            <Badge tono="primario">
              Captación
            </Badge>
          }
          pie="Registrados en el rango de fechas seleccionado"
        />

        <StatCard
          etiqueta="Pacientes Recurrentes"
          valor={(pacientes?.pacientesRecurrentes ?? 0).toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<UserCheck className="text-sky-600" />}
          pie="Pacientes con atenciones previas de control"
        />

        <StatCard
          etiqueta="Cumpleañeros del Mes"
          valor={(pacientes?.proximosCumpleanos?.length ?? 0).toString()}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<Gift className="text-pink-600" />}
          insignia={
            <Badge tono="villamax">
              Fidelización
            </Badge>
          }
          pie="Oportunidad de saludo y promoción por WhatsApp"
        />
      </div>

      {/* 2. Gráficos de Pacientes */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Gráfico A: Evolución Mensual de Pacientes */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Nuevos Pacientes por Mes
            </h3>
            <p className="text-xs text-on-surface-variant">
              Tendencia de captación durante los últimos meses
            </p>
          </div>

          <div className="h-64 w-full">
            {datosMeses.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                Sin datos históricos suficientes.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={datosMeses} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="mes" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip contentStyle={{ borderRadius: '8px', fontSize: '12px' }} />
                  <Bar dataKey="nuevos" name="Nuevos Pacientes" fill="#0D9488" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>

        {/* Gráfico B: Fuentes de Captación */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Canales y Fuentes de Captación
            </h3>
            <p className="text-xs text-on-surface-variant">
              Origen de atracción (redes, referidos, convenios, web)
            </p>
          </div>

          <div className="h-64 w-full">
            {datosFuentes.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay fuentes registradas en los pacientes.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={datosFuentes}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={80}
                    label={({ name, percent }) => `${name} (${((percent ?? 0) * 100).toFixed(0)}%)`}
                    labelLine={false}
                  >
                    {datosFuentes.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(val, name, item) => [
                      `${val} pacientes (${item.payload.porcentaje}%)`,
                      name,
                    ]}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>
      </div>

      {/* 3. Panel de Cumpleañeros para Fidelización */}
      <Card className="p-5">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Gift className="size-5 text-pink-600" />
            <div>
              <h3 className="font-display text-base font-semibold text-on-surface">
                Próximos Cumpleaños de Pacientes
              </h3>
              <p className="text-xs text-on-surface-variant">
                Envía saludos automáticos con beneficios para aumentar la retención clínica
              </p>
            </div>
          </div>
          <Badge tono="villamax">
            {pacientes?.proximosCumpleanos?.length ?? 0} pacientes
          </Badge>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(pacientes?.proximosCumpleanos ?? []).map((c) => (
            <div
              key={c.id}
              className="flex items-center justify-between rounded-panel border border-outline-variant/30 bg-surface p-3 transition-colors hover:border-pink-300"
            >
              <div className="flex items-center gap-3">
                <div className="flex size-9 items-center justify-center rounded-full bg-pink-100 text-pink-700 dark:bg-pink-950 dark:text-pink-300 font-bold text-xs">
                  {c.edad}
                </div>
                <div>
                  <p className="text-xs font-semibold text-on-surface">{c.nombreCompleto}</p>
                  <p className="text-xs text-on-surface-variant">
                    {formatFecha(c.fechaNacimiento)} · {c.sedeNombre}
                  </p>
                </div>
              </div>

              {c.telefono ? (
                <button
                  type="button"
                  onClick={() => enviarFelicitacionWhatsApp(c.telefono, c.nombreCompleto)}
                  className="inline-flex items-center gap-1 rounded-panel bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 dark:bg-emerald-950/40 dark:text-emerald-300"
                  title="Felicitar y enviar regalo por WhatsApp"
                >
                  <MessageCircle className="size-3" />
                  Saludar
                </button>
              ) : (
                <span className="text-xs text-on-surface-variant">Sin cel</span>
              )}
            </div>
          ))}

          {(pacientes?.proximosCumpleanos ?? []).length === 0 && (
            <div className="col-span-full py-6 text-center text-xs text-on-surface-variant">
              No hay cumpleaños registrados en los próximos 30 días.
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}
