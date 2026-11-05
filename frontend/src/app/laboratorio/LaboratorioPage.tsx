import { useState, useMemo } from 'react'
import {
  AlertCircle,
  Building2,
  Calendar,
  CheckCircle2,
  Clock,
  Eye,
  FlaskConical,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
} from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import { obtenerSesion } from '@/shared/auth'
import { RotuloMostrando } from '../layout/RotuloMostrando'
import { useCuentasLaboratorios, useOrdenesLaboratorio } from './api-laboratorio'
import { EstadoOrdenBadge } from './components/EstadoOrdenBadge'
import { NuevaOrdenModal } from './components/NuevaOrdenModal'
import { DetalleOrdenModal } from './components/DetalleOrdenModal'
import { LaboratoriosModal } from './components/LaboratoriosModal'
import type { EstadoOrden, LaboratorioCuenta, OrdenLaboratorio } from './types'

export function LaboratorioPage() {
  const sesion = obtenerSesion()
  const rol = sesion?.usuario.rol ?? 'RECEPCION'
  const esAdminGeneral = rol === 'ADMIN_GENERAL'
  const esOdontologo = rol === 'ODONTOLOGO'

  // Filtros
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoOrden | ''>('')
  const [tabActiva, setTabActiva] = useState<'TODAS' | 'EN_TALLER' | 'EN_CLINICA' | 'FINALIZADAS'>('TODAS')
  const [busqueda, setBusqueda] = useState('')
  const [vistaActiva, setVistaActiva] = useState<'ORDENES' | 'CUENTAS'>('ORDENES')

  // Modales
  const [modalNuevaOrdenAbierto, setModalNuevaOrdenAbierto] = useState(false)
  const [ordenDetalleId, setOrdenDetalleId] = useState<string | null>(null)
  const [ordenAEditar, setOrdenAEditar] = useState<OrdenLaboratorio | null>(null)
  const [modalLaboratoriosAbierto, setModalLaboratoriosAbierto] = useState(false)

  // Query de Órdenes
  const {
    data: ordenes = [],
    isLoading,
    isError,
    refetch,
    isFetching,
  } = useOrdenesLaboratorio({
    sedeId: sesion?.sedeActivaId ?? undefined,
    estado: estadoFiltro || undefined,
  })

  // Filtrado reactivo en cliente (por pestaña y término de búsqueda)
  const ordenesFiltradas = useMemo(() => {
    return ordenes.filter((o) => {
      // Filtro de pestaña
      if (tabActiva === 'EN_TALLER') {
        if (o.estado !== 'ENVIADO') return false
      } else if (tabActiva === 'EN_CLINICA') {
        if (o.estado !== 'EN_PRUEBA') return false
      } else if (tabActiva === 'FINALIZADAS') {
        if (o.estado !== 'FINALIZADO' && o.estado !== 'CANCELADO') return false
      }

      // Filtro de búsqueda
      if (!busqueda.trim()) return true
      const term = busqueda.toLowerCase().trim()
      const codigo = (o.codigo || '').toLowerCase()
      const paciente = (o.pacienteNombre || '').toLowerCase()
      const dni = (o.pacienteDni || '').toLowerCase()
      const trabajo = (o.tipoTrabajo || '').toLowerCase()
      const piezas = (o.piezasDentales || '').toLowerCase()
      const lab = (o.laboratorioNombre || '').toLowerCase()

      return (
        codigo.includes(term) ||
        paciente.includes(term) ||
        dni.includes(term) ||
        trabajo.includes(term) ||
        piezas.includes(term) ||
        lab.includes(term)
      )
    })
  }, [ordenes, tabActiva, busqueda])

  // Métricas y KPIs
  const kpis = useMemo(() => {
    const totalActivas = ordenes.filter(
      (o) => o.estado !== 'CANCELADO' && o.estado !== 'FINALIZADO'
    ).length
    const enLab = ordenes.filter((o) => o.estado === 'ENVIADO').length
    const enClinica = ordenes.filter((o) => o.estado === 'EN_PRUEBA').length
    const finalizadas = ordenes.filter((o) => o.estado === 'FINALIZADO').length

    return { totalActivas, enLab, enClinica, finalizadas }
  }, [ordenes])

  return (
    <div className="space-y-6 p-4 sm:p-6 max-w-7xl mx-auto">
      {/* Encabezado Principal */}
      <RotuloMostrando />
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200/80 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-200/80 flex items-center justify-center text-indigo-700 shadow-sm">
              <FlaskConical className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                Laboratorio Dental & Prótesis
              </h1>
              <p className="text-xs sm:text-sm text-slate-500">
                Órdenes protésicas, colorimetría VITA y trazabilidad clínica multisede.
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Button
            variant="secundario"
            onClick={() => setModalLaboratoriosAbierto(true)}
            className="text-xs font-semibold"
          >
            <Building2 className="w-3.5 h-3.5 mr-1.5 text-amber-600" />
            Directorio Laboratorios
          </Button>

          <Button
            variant="primario"
            onClick={() => {
              setOrdenAEditar(null)
              setModalNuevaOrdenAbierto(true)
            }}
            className="text-xs font-semibold shadow-sm"
          >
            <Plus className="w-4 h-4 mr-1.5" />
            Nueva Orden Protésica
          </Button>
        </div>
      </div>

      <div className="flex gap-2 border-b border-slate-200 pb-2">
        <button
          type="button"
          aria-pressed={vistaActiva === 'ORDENES'}
          onClick={() => setVistaActiva('ORDENES')}
          className={`rounded-lg px-3 py-2 text-xs font-bold ${
            vistaActiva === 'ORDENES' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
          }`}
        >
          Órdenes
        </button>
        {!esOdontologo && (
          <button
            type="button"
            aria-pressed={vistaActiva === 'CUENTAS'}
            onClick={() => setVistaActiva('CUENTAS')}
            className={`rounded-lg px-3 py-2 text-xs font-bold ${
              vistaActiva === 'CUENTAS' ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Cuentas con laboratorios
          </button>
        )}
      </div>

      {vistaActiva === 'CUENTAS' && !esOdontologo ? (
        <CuentasLaboratoriosPanel
          sedeId={sesion?.sedeActivaId ?? undefined}
        />
      ) : (
        <>
      {/* Tarjetas de Métricas Clínicas */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-1 transition-all hover:border-indigo-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Órdenes Activas
            </span>
            <div className="w-7 h-7 rounded-lg bg-sky-50 text-sky-700 flex items-center justify-center">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-black text-slate-900">{kpis.totalActivas}</p>
          <p className="text-xs text-slate-400">En curso entre taller y clínica</p>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-1 transition-all hover:border-amber-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              En Laboratorio
            </span>
            <div className="w-7 h-7 rounded-lg bg-amber-50 text-amber-700 flex items-center justify-center">
              <FlaskConical className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-black text-amber-700">{kpis.enLab}</p>
          <p className="text-xs text-slate-400">En confección protésica externa</p>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-1 transition-all hover:border-teal-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              En Clínica / Pruebas
            </span>
            <div className="w-7 h-7 rounded-lg bg-teal-50 text-teal-700 flex items-center justify-center">
              <Sparkles className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-black text-teal-800">{kpis.enClinica}</p>
          <p className="text-xs text-slate-400">Recibidas para prueba o instalación</p>
        </div>

        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-1 transition-all hover:border-emerald-200">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
              Instaladas / Entregadas
            </span>
            <div className="w-7 h-7 rounded-lg bg-emerald-50 text-emerald-700 flex items-center justify-center">
              <CheckCircle2 className="w-4 h-4" />
            </div>
          </div>
          <p className="text-2xl font-black text-emerald-800">{kpis.finalizadas}</p>
          <p className="text-xs text-slate-400">Prótesis cementadas con éxito</p>
        </div>
      </div>

      {/* Barra de Filtros, Búsqueda y Pestañas */}
      <div className="space-y-3">
        {/* Pestañas de Vista Rápida */}
        <div className="flex flex-wrap items-center gap-1.5 border-b border-slate-200 pb-2">
          <button
            type="button"
            onClick={() => setTabActiva('TODAS')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              tabActiva === 'TODAS'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Todas las Órdenes ({ordenes.length})
          </button>
          <button
            type="button"
            onClick={() => setTabActiva('EN_TALLER')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              tabActiva === 'EN_TALLER'
                ? 'bg-amber-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            En Taller / Confección ({kpis.enLab})
          </button>
          <button
            type="button"
            onClick={() => setTabActiva('EN_CLINICA')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              tabActiva === 'EN_CLINICA'
                ? 'bg-teal-700 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            En Clínica / Pruebas ({kpis.enClinica})
          </button>
          <button
            type="button"
            onClick={() => setTabActiva('FINALIZADAS')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              tabActiva === 'FINALIZADAS'
                ? 'bg-emerald-700 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Instaladas / Finalizadas ({kpis.finalizadas})
          </button>
        </div>

        {/* Controles de Búsqueda y Filtros de Sede/Estado */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-white p-3 rounded-2xl border border-slate-200 shadow-sm">
          <div className="relative flex-1 min-w-[240px]">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Buscar por paciente, código LAB, DNI, pieza dental o laboratorio..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-xs rounded-xl border border-slate-200 bg-slate-50 focus:bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">


            {/* Filtro Estado */}
            <select
              value={estadoFiltro}
              onChange={(e) => setEstadoFiltro(e.target.value as EstadoOrden | '')}
              className="h-9 px-2.5 rounded-xl border border-slate-200 bg-slate-50 text-xs text-slate-700 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            >
              <option value="">Todos los Estados</option>
              <option value="ENVIADO">En laboratorio</option>
              <option value="EN_PRUEBA">En prueba</option>
              <option value="FINALIZADO">Finalizado</option>
              <option value="CANCELADO">Cancelado</option>
            </select>

            <button
              type="button"
              onClick={() => refetch()}
              disabled={isFetching}
              title="Refrescar órdenes"
              className="w-9 h-9 flex items-center justify-center rounded-xl border border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100 transition-colors disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>
      </div>

      {/* Tabla Clínica de Órdenes */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="py-20 text-center space-y-2">
            <RefreshCw className="w-6 h-6 animate-spin text-indigo-600 mx-auto" />
            <p className="text-xs text-slate-500">Cargando órdenes de laboratorio dental...</p>
          </div>
        ) : isError ? (
          <div className="py-16 text-center space-y-3">
            <AlertCircle className="w-8 h-8 text-rose-500 mx-auto" />
            <p className="text-sm font-semibold text-slate-800">Error al consultar el servicio de laboratorio</p>
            <Button size="sm" variant="secundario" onClick={() => refetch()}>
              Reintentar
            </Button>
          </div>
        ) : ordenesFiltradas.length === 0 ? (
          <div className="py-16 text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-slate-50 border border-slate-200 flex items-center justify-center mx-auto text-slate-400">
              <FlaskConical className="w-6 h-6" />
            </div>
            <div>
              <p className="text-sm font-bold text-slate-800">No se encontraron órdenes de laboratorio</p>
              <p className="text-xs text-slate-400 mt-0.5">
                {busqueda || estadoFiltro
                  ? 'Pruebe ajustando los filtros de búsqueda.'
                  : 'Empiece registrando la primera orden protésica para laboratorio externo.'}
              </p>
            </div>
            {!busqueda && (
              <Button
                size="sm"
                variant="primario"
                onClick={() => setModalNuevaOrdenAbierto(true)}
              >
                <Plus className="w-3.5 h-3.5 mr-1" />
                Crear Primera Orden
              </Button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/80 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Código / Sede</th>
                  <th className="py-3 px-4">Paciente & Odontólogo</th>
                  <th className="py-3 px-4">Prótesis / Piezas</th>
                  <th className="py-3 px-4">Color VITA</th>
                  <th className="py-3 px-4">Laboratorio & Entrega</th>
                  <th className="py-3 px-4">Estado</th>
                  {!esOdontologo && <th className="py-3 px-4">Pago</th>}
                  <th className="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {ordenesFiltradas.map((orden) => {
                  return (
                    <tr
                      key={orden.id}
                      className="hover:bg-slate-50/60 transition-colors cursor-pointer group"
                      onClick={() => setOrdenDetalleId(orden.id)}
                    >
                      {/* Código / Sede */}
                      <td className="py-3.5 px-4">
                        <span className="font-mono font-bold text-indigo-700 block">
                          {orden.codigo}
                        </span>
                        <span className="text-xs text-slate-400 block mt-0.5">
                          {orden.sedeId}
                        </span>
                      </td>

                      {/* Paciente & Odontólogo */}
                      <td className="py-3.5 px-4">
                        <strong className="text-slate-900 block group-hover:text-indigo-600 transition-colors">
                          {orden.pacienteNombre}
                        </strong>
                        <span className="text-xs text-slate-400 block mt-0.5">
                          Tratante: {orden.especialistaNombre}
                        </span>
                      </td>

                      {/* Trabajo y Piezas */}
                      <td className="py-3.5 px-4">
                        <span className="font-semibold text-slate-800 block">
                          {orden.tipoTrabajo}
                        </span>
                        {orden.piezasDentales && (
                          <span className="text-xs font-mono text-slate-500 block mt-0.5">
                            Pieza(s): {orden.piezasDentales}
                          </span>
                        )}
                      </td>

                      {/* Tono VITA */}
                      <td className="py-3.5 px-4">
                        <div className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-slate-50 border border-slate-200">
                          <span className="w-3 h-3.5 rounded-sm border border-slate-300 bg-[#F0EBD7]" />
                          <span className="font-bold text-slate-800 text-xs">
                            {orden.colorTono || 'A2'}
                          </span>
                        </div>
                      </td>

                      {/* Laboratorio & Entrega Estimada */}
                      <td className="py-3.5 px-4">
                        <span className="font-medium text-slate-800 block">
                          {orden.laboratorioNombre}
                        </span>
                        <span className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                          <Calendar className="w-3 h-3 text-slate-400" />
                          {orden.fechaEstimadaEntrega ? (
                            <span>Est: <strong>{orden.fechaEstimadaEntrega}</strong></span>
                          ) : (
                            'Sin fecha'
                          )}
                        </span>
                      </td>

                      {/* Estado Clínico */}
                      <td className="py-3.5 px-4">
                        <EstadoOrdenBadge estado={orden.estado} tamano="sm" />
                      </td>

                      {!esOdontologo && (
                        <td className="py-3.5 px-4">
                          <EstadoPagoBadge estado={orden.estadoPago} />
                        </td>
                      )}

                      {/* Acciones */}
                      <td className="py-3.5 px-4 text-right">
                        {/* Solo evita que el clic en las acciones abra también el detalle de la fila; no es un control. */}
                        <div
                          role="presentation"
                          className="flex items-center justify-end gap-1.5"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <Button
                            size="sm"
                            variant="secundario"
                            onClick={() => setOrdenDetalleId(orden.id)}
                            className="text-xs h-7 px-2.5"
                          >
                            <Eye className="w-3.5 h-3.5 mr-1" />
                            Ver
                          </Button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
        </>
      )}

      {/* Modal Nueva Orden */}
      <NuevaOrdenModal
        abierto={modalNuevaOrdenAbierto}
        onCerrar={() => setModalNuevaOrdenAbierto(false)}
        ordenEditar={ordenAEditar}
        sedeIdInicial={sesion?.sedeActivaId ?? undefined}
        esOdontologo={esOdontologo}
      />

      {/* Modal Detalle de Orden */}
      <DetalleOrdenModal
        abierto={Boolean(ordenDetalleId)}
        onCerrar={() => setOrdenDetalleId(null)}
        ordenId={ordenDetalleId}
        onEditar={(orden) => {
          setOrdenDetalleId(null)
          setOrdenAEditar(orden)
          setModalNuevaOrdenAbierto(true)
        }}
      />

      {/* Modal Directorio de Laboratorios */}
      <LaboratoriosModal
        abierto={modalLaboratoriosAbierto}
        onCerrar={() => setModalLaboratoriosAbierto(false)}
        puedeEditar={esAdminGeneral}
      />
    </div>
  )
}

function CuentasLaboratoriosPanel({
  sedeId,
}: {
  sedeId?: string
}) {
  const { data: cuentas = [], isLoading, isError, refetch } = useCuentasLaboratorios({
    sedeId: sedeId || undefined,
  })

  return (
    <section aria-label="Cuentas con laboratorios" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-900">Cuentas con laboratorios</h2>
          <p className="text-sm text-slate-500">Facturado, pagado y saldo pendiente por proveedor.</p>
        </div>
      </div>

{isLoading ? (
        <p className="py-10 text-center text-sm text-slate-500">Cargando cuentas…</p>
      ) : isError ? (
        <div className="py-8 text-center">
          <p role="alert" className="text-sm text-rose-700">No se pudieron cargar las cuentas de laboratorio.</p>
          <Button size="sm" variant="secundario" onClick={() => refetch()}>Reintentar</Button>
        </div>
      ) : cuentas.length === 0 ? (
        <p className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          No hay órdenes facturables para los filtros seleccionados.
        </p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Laboratorio</th>
                <th className="px-4 py-3 text-right">Facturado</th>
                <th className="px-4 py-3 text-right">Pagado</th>
                <th className="px-4 py-3 text-right">Por pagar</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {cuentas.map((cuenta) => (
                <CuentaLaboratorioRow key={cuenta.laboratorioId} cuenta={cuenta} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function CuentaLaboratorioRow({ cuenta }: { cuenta: LaboratorioCuenta }) {
  return (
    <tr>
      <td className="px-4 py-3 font-semibold text-slate-900">
        <details>
          <summary className="cursor-pointer">{cuenta.laboratorioNombre}</summary>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full text-xs">
              <thead className="text-slate-500">
                <tr>
                  <th className="py-2 pr-3 text-left">Orden</th>
                  <th className="py-2 pr-3 text-left">Sede</th>
                  <th className="py-2 pr-3 text-right">Costo</th>
                  <th className="py-2 pr-3 text-right">Pagado</th>
                  <th className="py-2 pr-3 text-right">Saldo</th>
                  <th className="py-2 text-left">Estado</th>
                </tr>
              </thead>
              <tbody>
                {cuenta.ordenes.map((orden) => (
                  <tr key={orden.ordenId} className="border-t border-slate-100">
                    <td className="py-2 pr-3 font-mono">{orden.codigo}</td>
                    <td className="py-2 pr-3">{orden.sedeId}</td>
                    <td className="py-2 pr-3 text-right">{soles(orden.costoLaboratorio)}</td>
                    <td className="py-2 pr-3 text-right">{soles(orden.montoPagado)}</td>
                    <td className="py-2 pr-3 text-right">{soles(orden.saldoLaboratorio)}</td>
                    <td className="py-2"><EstadoPagoBadge estado={orden.estadoPago} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </details>
      </td>
      <td className="px-4 py-3 text-right">{soles(cuenta.totalFacturado)}</td>
      <td className="px-4 py-3 text-right">{soles(cuenta.totalPagado)}</td>
      <td className="px-4 py-3 text-right font-bold">{soles(cuenta.saldoPorPagar)}</td>
    </tr>
  )
}

function EstadoPagoBadge({ estado }: { estado?: string | null }) {
  const clases = estado === 'PAGADO'
    ? 'bg-emerald-100 text-emerald-800'
    : estado === 'PARCIAL'
      ? 'bg-amber-100 text-amber-800'
      : 'bg-slate-100 text-slate-700'
  const label = estado === 'PAGADO' ? 'Pagado' : estado === 'PARCIAL' ? 'Parcial' : 'Pendiente'
  return <span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${clases}`}>{label}</span>
}

function soles(monto?: number | null) {
  return monto == null ? '—' : `S/ ${monto.toFixed(2)}`
}
