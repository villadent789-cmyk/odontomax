import { nombreMetodoPago } from '@/shared/metodosPago'
import { useState } from 'react'
import {
  Banknote,
  DollarSign,
  Smartphone,
  TrendingUp,
  CreditCard,
  MessageCircle,
  Receipt,
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
  Legend,
} from 'recharts'
import { Card } from '@/design-system/components/Card'
import { StatCard } from '@/design-system/components/StatCard'
import { Badge } from '@/design-system/components/Badge'
import { formatSoles } from '@/shared/format'
import { formatoSaldo } from '@/shared/saldo'
import type { ReporteDeudoresDTO, ReporteFinancieroDTO } from './types'

interface FinanzasTabProps {
  financiero?: ReporteFinancieroDTO
  deudores?: ReporteDeudoresDTO
  cargando?: boolean
}

const COLORES_METODOS: Record<string, string> = {
  EFECTIVO: '#0D9488', // Teal
  YAPE: '#8B5CF6', // Purple
  PLIN: '#06B6D4', // Cyan
  TARJETA: '#3B82F6', // Blue
  TARJETA_CREDITO: '#2563EB',
  TARJETA_DEBITO: '#60A5FA',
  TRANSFERENCIA: '#F59E0B', // Amber
  OTRO: '#94A3B8',
}

export function FinanzasTab({ financiero, deudores, cargando }: FinanzasTabProps) {
  const [filtroDeudor, setFiltroDeudor] = useState('')

  // Métodos de pago formateados para Donut Chart
  const datosMetodos = (financiero?.ingresosPorMetodo ?? []).map((m) => ({
    name: nombreMetodoPago(m.metodo),
    value: m.total,
    cantidad: m.cantidad,
    color: COLORES_METODOS[m.metodo.toUpperCase()] ?? '#94A3B8',
  }))

  // Sedes o Tratamientos
  const datosSedes = (financiero?.ingresosPorSede ?? []).map((s) => ({
    nombre: s.sedeNombre,
    ingresos: s.totalIngresos,
    gastos: s.totalGastos,
    balance: s.balance,
  }))

  // Filtrado de deudores
  const listaDeudores = (deudores?.pacientes ?? []).filter((d) => {
    if (!filtroDeudor) return true
    const term = filtroDeudor.toLowerCase()
    return (
      d.pacienteNombre.toLowerCase().includes(term) ||
      d.dni.includes(term) ||
      (d.telefono ?? '').includes(term)
    )
  })

  const abrirWhatsAppDeudor = (telefono?: string | null, nombre?: string, saldo?: number) => {
    if (!telefono) return
    const telLimpio = telefono.replace(/\D/g, '')
    const mensaje = encodeURIComponent(
      `Estimado(a) ${nombre}, le saludamos cordialmente de Clínica Odontológica OdontoMax. Le recordamos que mantiene un saldo pendiente de ${formatSoles(
        saldo,
      )}. Puede abonar vía transferencia, Yape o en caja de su sede. ¡Muchas gracias!`,
    )
    window.open(`https://wa.me/51${telLimpio}?text=${mensaje}`, '_blank')
  }

  return (
    <div className="flex flex-col gap-6">
      {/* 1. KPIs Financieros */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard
          etiqueta="Total Ingresos Recaudados"
          valor={formatSoles(financiero?.totalIngresos ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<DollarSign className="text-teal-600" />}
          pie="Suma de todos los métodos de pago"
        />

        {/* Recaudación separada por método: lo que entra a la gaveta y lo que entra a la cuenta. */}
        <StatCard
          etiqueta="Total Efectivo (S/)"
          valor={formatSoles(financiero?.totalEfectivo ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<Banknote className="text-emerald-600" />}
          pie="Cobrado en efectivo (gaveta)"
        />

        <StatCard
          etiqueta="Total Yape / Plin (S/)"
          valor={formatSoles(financiero?.totalYape ?? 0)}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<Smartphone className="text-purple-600" />}
          pie="Cobrado por Yape o Plin (cuenta bancaria)"
        />

        <StatCard
          etiqueta="Total Gastos Operativos"
          valor={formatSoles(financiero?.totalGastos ?? 0)}
          cargando={cargando}
          tonoIcono="accion"
          icono={<Receipt className="text-rose-600" />}
          pie="Insumos, mantenimiento y servicios de sede"
        />

        <StatCard
          etiqueta="Utilidad Neta Estimada"
          valor={formatSoles(financiero?.balanceNeto ?? 0)}
          cargando={cargando}
          tonoIcono="primario"
          icono={<TrendingUp className="text-emerald-600" />}
          insignia={
            <Badge tono="primario">
              {((financiero?.totalIngresos ?? 0) > 0
                ? (((financiero?.balanceNeto ?? 0) / (financiero?.totalIngresos ?? 1)) * 100).toFixed(1)
                : 0)}
              % Margen
            </Badge>
          }
          pie="Ingresos menos egresos directos"
        />

        <StatCard
          etiqueta="Cuentas por Cobrar (Deuda Total)"
          valor={formatSoles(deudores?.deudaTotalAcumulada ?? financiero?.cuentasPorCobrar ?? 0)}
          cargando={cargando}
          tonoIcono="villamax"
          icono={<CreditCard className="text-amber-600" />}
          insignia={
            <Badge tono="villamax">
              {deudores?.totalPacientesDeudores ?? 0} pacientes
            </Badge>
          }
          pie="Saldos pendientes de tratamientos en curso"
        />
      </div>

      <Card className="p-5">
        <h3 className="mb-3 font-display text-base font-semibold text-on-surface">Gastos por categoría</h3>
        {(financiero?.gastosPorCategoria ?? []).length === 0 ? (
          <p className="text-sm text-on-surface-variant">No hay gastos registrados en este periodo.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {(financiero?.gastosPorCategoria ?? []).map((gasto) => (
              <li key={gasto.categoria} className="flex justify-between gap-4 py-2 text-sm">
                <span>{gasto.categoria}</span>
                <strong>{formatSoles(gasto.total)}</strong>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* 2. Gráficos de Finanzas */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Gráfico A: Ingresos por Método de Pago */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Distribución por Método de Pago
            </h3>
            <p className="text-xs text-on-surface-variant">
              Volumen monetario según medio de cancelación
            </p>
          </div>

          <div className="h-64 w-full">
            {datosMetodos.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay pagos registrados en este periodo.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={datosMetodos}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={3}
                  >
                    {datosMetodos.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(val) => [formatSoles(Number(val ?? 0)), 'Total']}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                  />
                  <Legend verticalAlign="bottom" height={36} iconSize={10} wrapperStyle={{ fontSize: '11px' }} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>

        {/* Gráfico B: Rendimiento por Sedes */}
        <Card className="flex flex-col p-5">
          <div className="mb-4">
            <h3 className="font-display text-base font-semibold text-on-surface">
              Ingresos vs Gastos por Sede
            </h3>
            <p className="text-xs text-on-surface-variant">
              Comparativa de rentabilidad por local clínico
            </p>
          </div>

          <div className="h-64 w-full">
            {datosSedes.length === 0 ? (
              <div className="flex h-full items-center justify-center text-xs text-on-surface-variant">
                No hay datos de sedes disponibles.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={datosSedes} margin={{ top: 10, right: 10, left: -15, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.15} />
                  <XAxis dataKey="nombre" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 10 }} tickFormatter={(v) => `S/${v}`} />
                  <Tooltip
                    formatter={(val) => [formatSoles(Number(val ?? 0)), '']}
                    contentStyle={{ borderRadius: '8px', fontSize: '12px' }}
                  />
                  <Bar dataKey="ingresos" name="Ingresos" fill="#0D9488" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="gastos" name="Gastos" fill="#E11D48" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>
      </div>

      {/* 3. Tabla Ejecutiva de Cuentas por Cobrar y Pacientes Deudores */}
      <Card className="p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="font-display text-base font-semibold text-on-surface">
              Reporte de Pacientes con Saldo Deudor
            </h3>
            <p className="text-xs text-on-surface-variant">
              Gestión de cobranza activa para recepcionistas y administración
            </p>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="Buscar por nombre, DNI o celular..."
              value={filtroDeudor}
              onChange={(e) => setFiltroDeudor(e.target.value)}
              className="rounded-panel border border-outline-variant/60 bg-surface px-3 py-1.5 text-xs text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:outline-none w-64"
            />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-on-surface">
            <thead className="border-b border-outline-variant/30 bg-surface-container text-xs font-semibold text-on-surface-variant uppercase tracking-wider">
              <tr>
                <th className="py-2.5 px-3">Paciente</th>
                <th className="py-2.5 px-3">DNI</th>
                <th className="py-2.5 px-3">Sede</th>
                <th className="py-2.5 px-3 text-right">Costo Total</th>
                <th className="py-2.5 px-3 text-right">Total Abonado</th>
                <th className="py-2.5 px-3 text-right">Saldo Deudor</th>
                <th className="py-2.5 px-3 text-center">Tratamientos</th>
                <th className="py-2.5 px-3 text-center">Acción</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20">
              {listaDeudores.map((d) => (
                <tr key={d.pacienteId} className="hover:bg-surface-container/50 transition-colors">
                  <td className="py-2.5 px-3 font-medium text-on-surface">
                    {d.pacienteNombre}
                  </td>
                  <td className="py-2.5 px-3 text-on-surface-variant">{d.dni}</td>
                  <td className="py-2.5 px-3 text-on-surface-variant">{d.sedeNombre}</td>
                  <td className="py-2.5 px-3 text-right text-on-surface-variant">
                    {formatSoles(d.totalTratamientos)}
                  </td>
                  <td className="py-2.5 px-3 text-right text-teal-600 dark:text-teal-400 font-medium">
                    {formatSoles(d.totalAbonado)}
                  </td>
                  <td className="py-2.5 px-3 text-right font-bold text-amber-700 dark:text-amber-400">
                    {formatoSaldo(d.saldoPendiente)}
                  </td>
                  <td className="py-2.5 px-3 text-center">
                    <Badge tono="neutro">{d.tratamientosPendientes} activos</Badge>
                  </td>
                  <td className="py-2.5 px-3 text-center">
                    {d.telefono ? (
                      <button
                        type="button"
                        onClick={() => abrirWhatsAppDeudor(d.telefono, d.pacienteNombre, d.saldoPendiente)}
                        className="inline-flex items-center gap-1 rounded-panel bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-900/50"
                        title="Enviar recordatorio amigable por WhatsApp"
                      >
                        <MessageCircle className="size-3" />
                        WhatsApp
                      </button>
                    ) : (
                      <span className="text-xs text-on-surface-variant">Sin celular</span>
                    )}
                  </td>
                </tr>
              ))}
              {listaDeudores.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-6 text-center text-xs text-on-surface-variant">
                    No se encontraron pacientes con deuda pendiente.
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
