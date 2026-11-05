import {
  DollarSign,
  Users,
  CalendarCheck,
  CreditCard,
  AlertTriangle,
  Clock,
  Package,
} from 'lucide-react'
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  Cell,
} from 'recharts'
import { StatCard } from '@/design-system/components/StatCard'
import { Badge } from '@/design-system/components/Badge'
import { Card } from '@/design-system/components/Card'
import { formatSoles, formatFecha } from '@/shared/format'
import { formatoSaldo } from '@/shared/saldo'
import type {
  ReporteCitasDTO,
  ReporteFinancieroDTO,
  ReporteInventarioDTO,
  ReportePacientesDTO,
  ReporteDeudoresDTO,
} from './types'

interface DashboardGeneralTabProps {
  financiero?: ReporteFinancieroDTO
  citas?: ReporteCitasDTO
  pacientes?: ReportePacientesDTO
  inventario?: ReporteInventarioDTO
  deudores?: ReporteDeudoresDTO
  cargando?: boolean
}

const COLORES_ESTADOS: Record<string, string> = {
  ATENDIDA: '#0D9488',
  CONFIRMADA: '#0284C7',
  PROGRAMADA: '#6366F1',
  EN_SALA: '#8B5CF6',
  PENDIENTE_WEB: '#F59E0B',
  CANCELADA: '#E11D48',
  FALTO: '#94A3B8',
  NO_ASISTIO: '#94A3B8',
}

export function DashboardGeneralTab({
  financiero,
  citas,
  pacientes,
  inventario,
  deudores,
  cargando,
}: DashboardGeneralTabProps) {
  const datosAreaFinanciera = (financiero?.ingresosPorDia ?? []).map((d) => ({
    fecha: formatFecha(d.fecha),
    ingresos: d.ingresos,
    gastos: d.gastos,
    balance: d.balance,
  }))

  const datosBarrasCitas = (citas?.citasPorEstado ?? []).map((e) => ({
    estado: e.estado,
    cantidad: e.cantidad,
    porcentaje: e.porcentaje,
    fill: COLORES_ESTADOS[e.estado.toUpperCase()] ?? '#64748B',
  }))

  return (
    <div className="flex flex-col gap-6">
      {/* 1. Tarjetas KPI Principales */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
        <StatCard
          etiqueta="Ingresos del Periodo"
          valor={formatSoles(financiero?.totalIngresos ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<DollarSign className="text-teal-600" />}
          insignia={
            <Badge tono="primario">
              Margen S/ {(financiero?.balanceNeto ?? 0).toLocaleString()}
            </Badge>
          }
          pie={`Gastos: ${formatSoles(financiero?.totalGastos ?? 0)} · Utilidad: ${formatSoles(financiero?.balanceNeto ?? 0)}`}
        />

        <StatCard
          etiqueta="Pacientes Nuevos"
          valor={(pacientes?.pacientesNuevos ?? 0).toString()}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Users className="text-cyan-600" />}
          insignia={
            <Badge tono="neutro">
              Recurrentes: {pacientes?.pacientesRecurrentes ?? 0}
            </Badge>
          }
          pie={`Total base activa: ${pacientes?.totalPacientes ?? 0} pacientes`}
        />

        <StatCard
          etiqueta="Citas Atendidas"
          valor={(citas?.atendidas ?? 0).toString()}
          cargando={cargando}
          tonoIcono="accion"
          icono={<CalendarCheck className="text-blue-600" />}
          insignia={
            <Badge tono="accion">
              {citas?.tasaAsistencia ?? 0}% Asistencia
            </Badge>
          }
          pie={`Total agendadas: ${citas?.totalCitas ?? 0} · Canceladas: ${citas?.canceladas ?? 0}`}
        />

        <StatCard
          etiqueta="Cuentas por Cobrar"
          valor={formatSoles(financiero?.cuentasPorCobrar ?? 0)}
          cargando={cargando}
          tonoIcono="accion"
          icono={<CreditCard className="text-amber-600" />}
          insignia={
            <Badge tono="villamax">
              {financiero?.tratamientosConSaldoPendiente ?? 0} pendientes
            </Badge>
          }
          pie={`Deudores acumulados: ${deudores?.totalPacientesDeudores ?? 0} pacientes`}
        />

        <StatCard
          etiqueta="Stock Crítico / Alertas"
          valor={(inventario?.productosStockBajo ?? 0).toString()}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<AlertTriangle className="text-rose-600" />}
          insignia={
            (inventario?.productosStockBajo ?? 0) > 0 ? (
              <Badge tono="peligro">
                Atención requerida
              </Badge>
            ) : (
              <Badge tono="neutro">En orden</Badge>
            )
          }
          pie={`Lotes por vencer: ${inventario?.productosPorVencer ?? 0} · Transf: ${inventario?.transferenciasPendientes ?? 0}`}
        />
      </div>

      {/* 2. Gráficos Principales */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Gráfico 1: Evolución de Ingresos vs Gastos */}
        <Card className="flex flex-col p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h3 className="font-display text-base font-semibold text-on-surface">
                Evolución de Ingresos vs Egresos
              </h3>
              <p className="text-xs text-on-surface-variant">
                Comparativa cronológica de ingresos cobrados y gastos operativos
              </p>
            </div>
            <div className="flex items-center gap-3 text-xs">
              <span className="flex items-center gap-1 font-medium text-teal-600 dark:text-teal-400">
                <span className="size-2 rounded-full bg-teal-600" /> Ingresos
              </span>
              <span className="flex items-center gap-1 font-medium text-rose-500 dark:text-rose-400">
                <span className="size-2 rounded-full bg-rose-500" /> Egresos
              </span>
            </div>
          </div>

          <div className="h-72 w-full">
            {datosAreaFinanciera.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay movimientos registrados en este rango de fechas.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={datosAreaFinanciera} margin={{ top: 10, right: 10, left: -15, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorIngresos" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#0D9488" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#0D9488" stopOpacity={0.0} />
                    </linearGradient>
                    <linearGradient id="colorGastos" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#E11D48" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#E11D48" stopOpacity={0.0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="fecha" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} tickFormatter={(v) => `S/${v}`} />
                  <Tooltip
                    formatter={(valor) => [formatSoles(Number(valor ?? 0)), '']}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px', border: '1px solid #e2e8f0' }}
                  />
                  <Area
                    type="monotone"
                    dataKey="ingresos"
                    name="Ingresos"
                    stroke="#0D9488"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorIngresos)"
                  />
                  <Area
                    type="monotone"
                    dataKey="gastos"
                    name="Gastos"
                    stroke="#E11D48"
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorGastos)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>

        {/* Gráfico 2: Embudo y Distribución de Citas */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Distribución de Citas Clínicas
            </h3>
            <p className="text-xs text-on-surface-variant">
              Estado operativo de citas agendadas
            </p>
          </div>

          <div className="h-72 w-full">
            {datosBarrasCitas.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay citas en este rango de fechas.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={datosBarrasCitas} layout="vertical" margin={{ top: 5, right: 20, left: 25, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis type="number" tick={{ fontSize: 11 }} />
                  <YAxis dataKey="estado" type="category" tick={{ fontSize: 10 }} />
                  <Tooltip
                    formatter={(v) => [`${v} citas`, 'Cantidad']}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px', border: '1px solid #e2e8f0' }}
                  />
                  <Bar dataKey="cantidad" radius={[0, 4, 4, 0]}>
                    {datosBarrasCitas.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.fill} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>
      </div>

      {/* 3. Panel de Alertas Prioritarias para Gerencia */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        {/* Alerta 1: Cuentas deudoras altas */}
        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CreditCard className="size-4 text-amber-600" />
              <h4 className="font-display text-sm font-semibold text-on-surface">
                Principales Saldos por Cobrar
              </h4>
            </div>
            <span className="text-xs text-on-surface-variant">
              Top {(deudores?.pacientes ?? []).slice(0, 3).length}
            </span>
          </div>
          <div className="flex flex-col divide-y divide-outline-variant/30">
            {(deudores?.pacientes ?? []).slice(0, 3).map((d) => (
              <div key={d.pacienteId} className="flex items-center justify-between py-2 text-xs">
                <div>
                  <p className="font-medium text-on-surface">{d.pacienteNombre}</p>
                  <p className="text-xs text-on-surface-variant">{d.sedeNombre} · DNI: {d.dni}</p>
                </div>
                <span className="font-semibold text-amber-700 dark:text-amber-400">
                  {formatoSaldo(d.saldoPendiente)}
                </span>
              </div>
            ))}
            {(deudores?.pacientes ?? []).length === 0 && (
              <p className="py-4 text-center text-xs text-on-surface-variant">
                No hay cuentas pendientes por cobrar.
              </p>
            )}
          </div>
        </Card>

        {/* Alerta 2: Stock bajo o crítico */}
        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Package className="size-4 text-rose-600" />
              <h4 className="font-display text-sm font-semibold text-on-surface">
                Insumos en Stock Crítico
              </h4>
            </div>
            <span className="text-xs text-on-surface-variant">
              {inventario?.alertasStock?.length ?? 0} alertas
            </span>
          </div>
          <div className="flex flex-col divide-y divide-outline-variant/30">
            {(inventario?.alertasStock ?? []).slice(0, 3).map((p) => (
              <div key={p.productoId} className="flex items-center justify-between py-2 text-xs">
                <div>
                  <p className="font-medium text-on-surface">{p.nombre}</p>
                  <p className="text-xs text-on-surface-variant">{p.codigo} · Mín: {p.stockMinimo}</p>
                </div>
                <Badge
                  tono={p.stockActual === 0 ? 'peligro' : 'villamax'}
                >
                  {p.stockActual} unid.
                </Badge>
              </div>
            ))}
            {(inventario?.alertasStock ?? []).length === 0 && (
              <p className="py-4 text-center text-xs text-on-surface-variant">
                Todos los productos tienen stock saludable.
              </p>
            )}
          </div>
        </Card>

        {/* Alerta 3: Lotes por vencer */}
        <Card className="p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Clock className="size-4 text-amber-600" />
              <h4 className="font-display text-sm font-semibold text-on-surface">
                Lotes de Insumos por Vencer
              </h4>
            </div>
            <span className="text-xs text-on-surface-variant">
              {inventario?.lotesVencimiento?.length ?? 0} lotes
            </span>
          </div>
          <div className="flex flex-col divide-y divide-outline-variant/30">
            {(inventario?.lotesVencimiento ?? []).slice(0, 3).map((l) => (
              <div key={l.movimientoId} className="flex items-center justify-between py-2 text-xs">
                <div>
                  <p className="font-medium text-on-surface">{l.productoNombre}</p>
                  <p className="text-xs text-on-surface-variant">Lote: {l.lote} · Vence: {formatFecha(l.fechaVencimiento)}</p>
                </div>
                <span className={`text-xs font-semibold ${l.diasParaVencer < 0 ? 'text-rose-600' : 'text-amber-600'}`}>
                  {l.diasParaVencer < 0 ? 'Vencido' : `${l.diasParaVencer} días`}
                </span>
              </div>
            ))}
            {(inventario?.lotesVencimiento ?? []).length === 0 && (
              <p className="py-4 text-center text-xs text-on-surface-variant">
                No hay lotes próximos a caducar.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
