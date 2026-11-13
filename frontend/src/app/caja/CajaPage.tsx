import { nombreMetodoPago } from '@/shared/metodosPago'
import { tieneRol } from '@/shared/auth/sesion'
import { useState, useMemo } from 'react'
import {
  Wallet,
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  Lock,
  History,
  Receipt,
  Building2,
  Search,
  X,
} from 'lucide-react'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import { Card } from '@/design-system/components/Card'
import { ErrorState } from '@/design-system/components/ErrorState'
import { Input } from '@/design-system/components/Input'
import { Skeleton } from '@/design-system/components/Skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/design-system/components/Tabs'
import { formatSoles, formatFechaHora } from '@/shared/format'
import { useSesion } from '@/shared/auth'
import { RotuloMostrando } from '@/app/layout/RotuloMostrando'
import {
  useCajaActiva,
  useHistorialCajas,
  useCajaResumenDiario,
  usePagosSede,
  useAnularPagoMutation,
  type CajaMovimientoDTO,
  type PagoDTO,
} from './api-caja'
import { AbrirCajaModal } from './AbrirCajaModal'
import { CerrarCajaModal } from './CerrarCajaModal'
import { RegistrarMovimientoModal } from './RegistrarMovimientoModal'
import { AnularPagoDialog } from './AnularPagoDialog'
import { RegistrarPagoModal } from './RegistrarPagoModal'
import { ImprimirComprobanteModal } from './ImprimirComprobanteModal'
import { useDatosSede } from './useDatosSede'
import type { TratamientoDTO } from '../tratamientos/api-tratamientos'
import { api, textoDeError } from '@/shared/http'
import { imprimirDocumento } from '@/shared/imprimir'
import { documentoReporteDiario, type ReporteDiario } from './documentoReporteDiario'

type PestañaCaja = 'turno' | 'resumen' | 'historial' | 'pagos'
type PeriodoFiltro = 'todos' | 'hoy' | 'semana' | 'mes'
type MetodoFiltro = 'TODOS' | 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TARJETA_DEBITO' | 'TARJETA_CREDITO'

function fechaHoyLima(): string {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const parte = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? ''
  return `${parte('year')}-${parte('month')}-${parte('day')}`
}

function coincidePeriodo(fechaStr: string, periodo: PeriodoFiltro): boolean {
  if (periodo === 'todos') return true
  const f = new Date(fechaStr)
  if (isNaN(f.getTime())) return true
  const hoy = new Date()

  if (periodo === 'hoy') {
    return (
      f.getFullYear() === hoy.getFullYear() &&
      f.getMonth() === hoy.getMonth() &&
      f.getDate() === hoy.getDate()
    )
  }

  if (periodo === 'semana') {
    const diaSemana = hoy.getDay()
    const diffLunes = diaSemana === 0 ? -6 : 1 - diaSemana
    const lunes = new Date(hoy)
    lunes.setDate(hoy.getDate() + diffLunes)
    lunes.setHours(0, 0, 0, 0)

    const domingo = new Date(lunes)
    domingo.setDate(lunes.getDate() + 6)
    domingo.setHours(23, 59, 59, 999)

    return f >= lunes && f <= domingo
  }

  if (periodo === 'mes') {
    return f.getFullYear() === hoy.getFullYear() && f.getMonth() === hoy.getMonth()
  }

  return true
}

export function CajaPage() {
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId ?? 'sede-chilca-1'
  const rol = sesion?.usuario.rol ?? 'RECEPCION'
  const esAdmin = rol === 'ADMIN_GENERAL' || rol === 'ADMIN_SEDE'
  const puedeVerResumen = esAdmin || tieneRol(sesion?.usuario, 'RECEPCION')

  const [pestaña, setPestaña] = useState<PestañaCaja>('turno')
  const [imprimiendoReporte, setImprimiendoReporte] = useState(false)
  const [errorReporte, setErrorReporte] = useState<string | null>(null)

  async function imprimirReporteDelDia() {
    setErrorReporte(null)
    setImprimiendoReporte(true)
    try {
      const reporte = await api.get<ReporteDiario>(
        `/caja/reporte-diario?sedeId=${encodeURIComponent(sedeId)}&fecha=${fechaHoyLima()}`,
      )
      await imprimirDocumento(documentoReporteDiario(reporte))
    } catch (e) {
      setErrorReporte(textoDeError(e, 'No se pudo preparar el reporte del día.'))
    } finally {
      setImprimiendoReporte(false)
    }
  }

  // Filtros para la pestaña de Cobros Realizados
  const [periodoFiltro, setPeriodoFiltro] = useState<PeriodoFiltro>('todos')
  const [metodoFiltro, setMetodoFiltro] = useState<MetodoFiltro>('TODOS')
  const [busquedaCobros, setBusquedaCobros] = useState('')

  // Modales
  const [modalAbrir, setModalAbrir] = useState(false)
  const [modalCerrar, setModalCerrar] = useState(false)
  const [modalMovimiento, setModalMovimiento] = useState(false)
  const [modalPago, setModalPago] = useState(false)
  const [pagoParaComprobante, setPagoParaComprobante] = useState<PagoDTO | null>(null)
  const datosSedeComprobante = useDatosSede(pagoParaComprobante?.sedeId)

  // Consultas React Query
  const {
    data: cajaActiva,
    isLoading: cargandoCaja,
    isError: errorCaja,
    refetch: recargarCaja,
  } = useCajaActiva(sedeId)

  const {
    data: historial = [],
    isLoading: cargandoHistorial,
  } = useHistorialCajas(puedeVerResumen ? sedeId : undefined)
  const {
    data: resumenDiario,
    isLoading: cargandoResumen,
    isError: errorResumen,
    refetch: recargarResumen,
  } = useCajaResumenDiario(puedeVerResumen ? sedeId : undefined, fechaHoyLima())

  const {
    data: pagos = [],
    isLoading: cargandoPagos,
    refetch: recargarPagos,
  } = usePagosSede(sedeId)

  const anular = useAnularPagoMutation()
  const [pagoAAnular, setPagoAAnular] = useState<PagoDTO | null>(null)
  const [errorAnulacion, setErrorAnulacion] = useState<string | null>(null)
  const tieneTurnoAbierto = Boolean(cajaActiva && cajaActiva.estado === 'ABIERTA')

  // Cobros filtrados
  const pagosFiltrados = useMemo(() => {
    const termino = busquedaCobros.toLowerCase().trim()
    return pagos.filter((p) => {
      if (!coincidePeriodo(p.pagadoEn, periodoFiltro)) return false
      if (metodoFiltro !== 'TODOS' && p.metodoPago !== metodoFiltro) return false
      if (termino) {
        const paciente = (p.pacienteNombre ?? '').toLowerCase()
        const dni = (p.pacienteDni ?? '').toLowerCase()
        const comp = (p.comprobante ?? '').toLowerCase()
        const trat = (p.tratamientoNombre ?? '').toLowerCase()
        if (
          !paciente.includes(termino) &&
          !dni.includes(termino) &&
          !comp.includes(termino) &&
          !trat.includes(termino)
        ) {
          return false
        }
      }
      return true
    })
  }, [pagos, periodoFiltro, metodoFiltro, busquedaCobros])

  const totalRecaudado = useMemo(() => {
    return pagosFiltrados.filter((p) => !p.anulado).reduce((acc, p) => acc + (p.monto ?? 0), 0)
  }, [pagosFiltrados])

  const totalPlin = useMemo(() => {
    return pagosFiltrados
      .filter((p) => !p.anulado && p.metodoPago === 'PLIN')
      .reduce((acc, p) => acc + (p.monto ?? 0), 0)
  }, [pagosFiltrados])

  const totalTarjeta = useMemo(() => {
    return pagosFiltrados
      .filter((p) => !p.anulado && (p.metodoPago === 'TARJETA_DEBITO' || p.metodoPago === 'TARJETA_CREDITO'))
      .reduce((acc, p) => acc + (p.monto ?? 0), 0)
  }, [pagosFiltrados])

  const totalEfectivo = useMemo(() => {
    return pagosFiltrados
      .filter((p) => !p.anulado && p.metodoPago === 'EFECTIVO')
      .reduce((acc, p) => acc + (p.monto ?? 0), 0)
  }, [pagosFiltrados])

  const totalYape = useMemo(() => {
    return pagosFiltrados
      .filter((p) => !p.anulado && p.metodoPago === 'YAPE')
      .reduce((acc, p) => acc + (p.monto ?? 0), 0)
  }, [pagosFiltrados])

  function handleAnularPago(pago: PagoDTO) {
    setErrorAnulacion(null)
    setPagoAAnular(pago)
  }

  async function confirmarAnulacion(motivo: string) {
    if (!pagoAAnular) return
    setErrorAnulacion(null)
    try {
      // La mutación refresca caja, pagos, saldo del paciente y tratamiento (también en otras pantallas).
      await anular.mutateAsync({ id: pagoAAnular.id, motivo })
      setPagoAAnular(null)
    } catch (err) {
      setErrorAnulacion(textoDeError(err, 'No se pudo anular el comprobante.'))
    }
  }

  return (
    <div className="space-y-6">
      {/* Encabezado Principal Stitch B8 */}
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="font-display text-2xl font-bold text-on-surface sm:text-3xl">
              Caja
            </h1>
            <Badge tono={tieneTurnoAbierto ? 'primario' : 'neutro'}>
              {tieneTurnoAbierto ? 'Turno Abierto' : 'Sin Turno Activo'}
            </Badge>
            <RotuloMostrando />
          </div>
          <p className="text-body-sm text-on-surface-variant mt-1 flex items-center gap-2">
            <Building2 className="size-4 text-primary" aria-hidden />
            <span>Sede activa: <strong className="text-on-surface">{cajaActiva?.sedeNombre ?? sedeId}</strong></span>
            <span>•</span>
            <span>Caja en cero · ingresos, egresos y cierres por periodo</span>
          </p>
        </div>

        {/* Acciones de Cabecera */}
        <div className="flex flex-wrap items-center gap-2.5">
          <Button
            variant="accion"
            onClick={() => setModalPago(true)}
            className="shadow-sm"
          >
            <Plus className="size-4 mr-1.5" aria-hidden />
            Nuevo Cobro / Abono
          </Button>

          {!tieneTurnoAbierto ? (
            <Button
              variant="primario"
              onClick={() => setModalAbrir(true)}
            >
              <Wallet className="size-4 mr-1.5" aria-hidden />
              Abrir Turno de Caja
            </Button>
          ) : (
            <>
              <Button
                variant="secundario"
                onClick={() => setModalMovimiento(true)}
              >
                <ArrowDownLeft className="size-4 mr-1.5 text-error" aria-hidden />
                Egreso Manual
              </Button>
              <Button
                variant="peligro"
                onClick={() => setModalCerrar(true)}
              >
                <Lock className="size-4 mr-1.5" aria-hidden />
                Cerrar periodo
              </Button>
            </>
          )}
        </div>
      </header>

      {/* Pestañas de Navegación */}
      <Tabs value={pestaña} onValueChange={(v) => setPestaña(v as PestañaCaja)}>
        <TabsList>
          <TabsTrigger value="turno">
            <Wallet className="size-4 mr-2" aria-hidden />
            Turno Actual (Operaciones)
          </TabsTrigger>
          <TabsTrigger value="pagos">
            <Receipt className="size-4 mr-2" aria-hidden />
            Cobros Realizados ({pagos.length})
          </TabsTrigger>
          {puedeVerResumen && (
            <TabsTrigger value="resumen">
              Resumen del día
            </TabsTrigger>
          )}
          {puedeVerResumen && (
            <TabsTrigger value="historial">
              <History className="size-4 mr-2" aria-hidden />
              Historial de Cierres
            </TabsTrigger>
          )}
        </TabsList>
      </Tabs>

      {/* ────────────────── PESTAÑA 1: TURNO ACTUAL ────────────────── */}
      {pestaña === 'turno' && (
        <div className="space-y-6">
          {cargandoCaja ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-28 rounded-card" />
              ))}
            </div>
          ) : errorCaja ? (
            <ErrorState
              titulo="Error al cargar información de caja"
              descripcion="No se pudo obtener el estado del turno de caja de la sede."
              onReintentar={() => recargarCaja()}
            />
          ) : !cajaActiva ? (
            /* Estado Vacío: Sin caja abierta */
            <div className="rounded-card border border-outline bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-surface-container-high text-on-surface-variant">
                <Lock className="size-8 text-primary" aria-hidden />
              </div>
              <div className="max-w-md mx-auto space-y-2">
                <h3 className="font-display text-xl font-bold text-on-surface">
                  No hay un turno de caja abierto en esta sede
                </h3>
                <p className="text-body-sm text-on-surface-variant">
                  Abre la caja en cero para registrar cobros y egresos del periodo.
                </p>
              </div>
              <div className="pt-2">
                <Button variant="accion" onClick={() => setModalAbrir(true)}>
                  <Plus className="size-4 mr-1.5" aria-hidden />
                  Abrir Turno de Caja Ahora
                </Button>
              </div>
            </div>
          ) : (
            /* Turno Abierto: Métricas y Movimientos */
            <>
              {/* Resumen por método de la sesión abierta */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {([
                  ['Efectivo', cajaActiva.totalIngresosEfectivo, cajaActiva.totalEgresosEfectivo, 'text-emerald-700'],
                  ['Yape', cajaActiva.totalIngresosYape ?? cajaActiva.totalYape, cajaActiva.totalEgresosYape, 'text-purple-700'],
                  ['Plin', cajaActiva.totalIngresosPlin, cajaActiva.totalEgresosPlin, 'text-indigo-700'],
                  ['Tarjeta de débito', cajaActiva.totalIngresosTarjetaDebito, 0, 'text-sky-700'],
                  ['Tarjeta de crédito', cajaActiva.totalIngresosTarjetaCredito, 0, 'text-sky-700'],
                ] as const).map(([metodo, ingresos, egresos, color]) => (
                  // Una tarjeta por método: lo que debería haber (cobrado menos egresos) y de dónde sale.
                  <Card key={metodo} className="p-4 border border-outline bg-surface-container-lowest space-y-2">
                    <span className={`text-xs font-semibold uppercase tracking-wider ${color}`}>Neto esperado · {metodo}</span>
                    <p className="font-mono text-2xl font-bold text-on-surface">{formatSoles((ingresos ?? 0) - (egresos ?? 0))}</p>
                    <div className="space-y-0.5 border-t border-outline-variant/50 pt-2 text-xs text-on-surface-variant">
                      <p className="flex items-center justify-between gap-2">
                        <span className="flex items-center gap-1"><ArrowDownLeft className="size-3" aria-hidden /> Cobrado</span>
                        <span className="font-mono font-semibold text-on-surface">{formatSoles(ingresos ?? 0)}</span>
                      </p>
                      <p className="flex items-center justify-between gap-2">
                        <span className="flex items-center gap-1"><ArrowUpRight className="size-3" aria-hidden /> Egresos</span>
                        <span className="font-mono font-semibold text-on-surface">-{formatSoles(egresos ?? 0)}</span>
                      </p>
                    </div>
                  </Card>
                ))}
              </div>

              {/* Tabla de Movimientos del Turno */}
              <section className="rounded-card border border-outline bg-surface-container-lowest shadow-card overflow-hidden">
                <div className="flex items-center justify-between p-4 border-b border-outline">
                  <div>
                    <h3 className="font-display text-base font-bold text-on-surface">
                      Movimientos del Turno
                    </h3>
                    <p className="text-xs text-on-surface-variant">
                      Abierto el {formatFechaHora(cajaActiva.abiertaEn)} por{' '}
                      <strong className="text-on-surface">{cajaActiva.abiertaPorNombre ?? 'Usuario'}</strong>
                    </p>
                  </div>
                  <Button
                    variant="secundario"
                    onClick={() => setModalMovimiento(true)}
                    className="text-xs py-1.5 px-3"
                  >
                    + Registrar Egreso
                  </Button>
                </div>

                {!cajaActiva.movimientos || cajaActiva.movimientos.length === 0 ? (
                  <div className="p-8 text-center text-xs text-on-surface-variant">
                    No hay movimientos registrados aún en este turno.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                      <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                        <tr>
                          <th className="py-2.5 px-4">Hora</th>
                          <th className="py-2.5 px-4">Tipo</th>
                          <th className="py-2.5 px-4">Concepto / Detalle</th>
                          <th className="py-2.5 px-4">Medio</th>
                          <th className="py-2.5 px-4 text-right">Monto (S/)</th>
                          <th className="py-2.5 px-4">Registrado Por</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline">
                        {cajaActiva.movimientos.map((m: CajaMovimientoDTO) => {
                          const esIngreso = m.tipo === 'INGRESO'
                          return (
                            <tr key={m.id} className="hover:bg-surface-container-low/50">
                              <td className="py-2.5 px-4 font-mono text-on-surface-variant">
                                {formatFechaHora(m.registradoEn)}
                              </td>
                              <td className="py-2.5 px-4">
                                <Badge tono={esIngreso ? 'primario' : 'peligro'}>
                                  {esIngreso ? 'Ingreso' : 'Egreso'}
                                </Badge>
                              </td>
                              <td className="py-2.5 px-4 font-medium text-on-surface">
                                {m.concepto}
                              </td>
                              <td className="py-2.5 px-4">
                                <span className="font-semibold text-on-surface-variant">
                                  {nombreMetodoPago(m.metodoPago ?? 'EFECTIVO')}
                                </span>
                              </td>
                              <td
                                className={`py-2.5 px-4 text-right font-mono font-bold ${
                                  esIngreso ? 'text-emerald-700' : 'text-error'
                                }`}
                              >
                                {esIngreso ? '+' : '-'}
                                {formatSoles(m.monto)}
                              </td>
                              <td className="py-2.5 px-4 text-on-surface-variant">
                                {m.registradoPorNombre ?? 'Personal'}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </div>
      )}

      {pestaña === 'resumen' && puedeVerResumen && (
        <section className="rounded-card border border-outline bg-surface-container-lowest shadow-card overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 p-4 border-b border-outline">
            <div>
              <h3 className="font-display text-base font-bold text-on-surface">Resumen del día</h3>
              <p className="text-xs text-on-surface-variant">
                Cobros y egresos registrados hoy · {resumenDiario?.fecha ?? fechaHoyLima()}
              </p>
            </div>
            <Button variant="secundario" onClick={imprimirReporteDelDia} disabled={imprimiendoReporte}>
              {imprimiendoReporte ? 'Preparando…' : 'Imprimir reporte del día'}
            </Button>
            {errorReporte && (
              <p role="alert" className="w-full text-sm text-error">
                {errorReporte}
              </p>
            )}
          </div>
          {cargandoResumen ? (
            <div className="grid gap-4 p-4 sm:grid-cols-3">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-card" />)}
            </div>
          ) : errorResumen ? (
            <ErrorState titulo="No se pudo cargar el resumen diario" onReintentar={() => recargarResumen()} />
          ) : (
            <div className="overflow-x-auto p-4">
              <table className="w-full text-left text-sm">
                <thead className="bg-surface-container-low text-on-surface-variant">
                  <tr>
                    <th className="p-3">Medio</th>
                    <th className="p-3 text-right">Cobrado</th>
                    <th className="p-3 text-right">Egresos</th>
                    <th className="p-3 text-right">Neto</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline">
                  {([
                    ['Efectivo', resumenDiario?.ingresosEfectivo, resumenDiario?.egresosEfectivo],
                    ['Yape', resumenDiario?.ingresosYape, resumenDiario?.egresosYape],
                    ['Plin', resumenDiario?.ingresosPlin, resumenDiario?.egresosPlin],
                    ['Tarjeta de débito', resumenDiario?.ingresosTarjetaDebito, 0],
                    ['Tarjeta de crédito', resumenDiario?.ingresosTarjetaCredito, resumenDiario?.egresosTarjeta],
                  ] as const).map(([metodo, ingreso, egreso]) => (
                    <tr key={metodo}>
                      <th className="p-3 font-medium">{metodo}</th>
                      <td className="p-3 text-right font-mono">{formatSoles(ingreso ?? 0)}</td>
                      <td className="p-3 text-right font-mono text-error">{formatSoles(egreso ?? 0)}</td>
                      <td className="p-3 text-right font-mono font-semibold">
                        {formatSoles((ingreso ?? 0) - (egreso ?? 0))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* ────────────────── PESTAÑA 2: COBROS REALIZADOS ────────────────── */}
      {pestaña === 'pagos' && (
        <section className="rounded-card border border-outline bg-surface-container-lowest shadow-card overflow-hidden">
          <div className="flex flex-col gap-3 p-4 border-b border-outline sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h3 className="font-display text-base font-bold text-on-surface">
                Comprobantes de Pago Emitidos
              </h3>
              <p className="text-xs text-on-surface-variant">
                Historial de cobros y abonos en la sede {sedeId}
              </p>
            </div>
            <Button
              variant="accion"
              onClick={() => setModalPago(true)}
              className="text-xs py-1.5 px-3 self-start sm:self-auto"
            >
              <Plus className="size-3.5 mr-1.5" aria-hidden />
              Nuevo Cobro
            </Button>
          </div>

          {cargandoPagos ? (
            <div className="p-8 space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : pagos.length === 0 ? (
            <div className="p-12 text-center text-xs text-on-surface-variant space-y-2">
              <Receipt className="size-8 mx-auto text-on-surface-variant/50" aria-hidden />
              <p>No se han emitido comprobantes de pago aún en esta sede.</p>
            </div>
          ) : (
            <>
              {/* Barra de Filtros, Búsqueda y Totales */}
              <div className="p-4 bg-surface-container-low/40 border-b border-outline space-y-3">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  {/* Selector de Período */}
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-xs font-semibold text-on-surface-variant mr-1">Período:</span>
                    {(
                      [
                        { id: 'todos', label: 'Todos' },
                        { id: 'hoy', label: 'Hoy' },
                        { id: 'semana', label: 'Esta Semana' },
                        { id: 'mes', label: 'Este Mes' },
                      ] as const
                    ).map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setPeriodoFiltro(item.id)}
                        className={`px-3 py-1 text-xs rounded-full font-medium transition-all ${
                          periodoFiltro === item.id
                            ? 'bg-acento text-on-acento shadow-xs'
                            : 'bg-surface-container-lowest border border-outline/70 text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                        }`}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>

                  {/* Selector de Método de Pago */}
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-semibold text-on-surface-variant mr-1">Método:</span>
                    <select
                      value={metodoFiltro}
                      onChange={(e) => setMetodoFiltro(e.target.value as MetodoFiltro)}
                      className="rounded-md border border-outline bg-surface-container-lowest px-2.5 py-1 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
                    >
                      <option value="TODOS">Todos los métodos</option>
                      <option value="EFECTIVO">💵 Solo Efectivo</option>
                      <option value="YAPE">📱 Solo Yape</option>
                      <option value="PLIN">📱 Solo Plin</option>
                      <option value="TARJETA_DEBITO">💳 Solo tarjeta de débito</option>
                      <option value="TARJETA_CREDITO">💳 Solo tarjeta de crédito</option>
                    </select>
                  </div>
                </div>

                {/* Input Buscador */}
                <div>
                  <Input
                    type="text"
                    placeholder="Buscar por paciente, DNI, comprobante o tratamiento..."
                    value={busquedaCobros}
                    onChange={(e) => setBusquedaCobros(e.target.value)}
                    icono={<Search className="size-4" />}
                    className="text-xs h-9 bg-surface-container-lowest"
                    sufijo={
                      busquedaCobros ? (
                        <button
                          type="button"
                          onClick={() => setBusquedaCobros('')}
                          className="text-on-surface-variant hover:text-on-surface p-1"
                          title="Limpiar búsqueda"
                        >
                          <X className="size-3.5" />
                        </button>
                      ) : undefined
                    }
                  />
                </div>

                {/* Resumen de totales filtrados */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 text-xs text-on-surface-variant pt-1 border-t border-outline/50">
                  <span>
                    Mostrando <strong className="text-on-surface font-semibold">{pagosFiltrados.length}</strong> {pagosFiltrados.length === 1 ? 'comprobante' : 'comprobantes'}
                    {pagosFiltrados.length !== pagos.length && (
                      <span> (de un total de {pagos.length})</span>
                    )}
                  </span>
                  <div className="flex flex-wrap items-center gap-3 font-mono">
                    <span>Efectivo: <strong className="text-emerald-700 font-semibold">{formatSoles(totalEfectivo)}</strong></span>
                    <span>•</span>
                    <span>Yape: <strong className="text-purple-700 font-semibold">{formatSoles(totalYape)}</strong></span>
                    <span>Plin: <strong className="text-indigo-700 font-semibold">{formatSoles(totalPlin)}</strong></span>
                    <span>Tarjetas: <strong className="text-sky-700 font-semibold">{formatSoles(totalTarjeta)}</strong></span>
                    <span>•</span>
                    <span>Total Recaudado: <strong className="text-primary font-bold">{formatSoles(totalRecaudado)}</strong></span>
                  </div>
                </div>
              </div>

              {/* Si no hay resultados con el filtro actual */}
              {pagosFiltrados.length === 0 ? (
                <div className="p-12 text-center text-xs text-on-surface-variant space-y-3">
                  <Search className="size-8 mx-auto text-on-surface-variant/40" aria-hidden />
                  <p>No se encontraron comprobantes que coincidan con los filtros aplicados.</p>
                  <Button
                    type="button"
                    variant="secundario"
                    className="text-xs py-1 px-3"
                    onClick={() => {
                      setPeriodoFiltro('todos')
                      setMetodoFiltro('TODOS')
                      setBusquedaCobros('')
                    }}
                  >
                    Restablecer filtros
                  </Button>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                      <tr>
                        <th className="py-2.5 px-4">Comprobante</th>
                        <th className="py-2.5 px-4">Fecha y Hora</th>
                        <th className="py-2.5 px-4">Paciente</th>
                        <th className="py-2.5 px-4">Tratamiento / Concepto</th>
                        <th className="py-2.5 px-4">Método</th>
                        <th className="py-2.5 px-4 text-right">Monto (S/)</th>
                        <th className="py-2.5 px-4">Estado</th>
                        {esAdmin && <th className="py-2.5 px-4 text-center">Acción</th>}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline">
                      {pagosFiltrados.map((p) => (
                        <tr
                          key={p.id}
                          className={`hover:bg-surface-container-low/50 ${p.anulado ? 'opacity-60 bg-surface-container-low/20' : ''}`}
                        >
                          <td className="py-2.5 px-4 font-mono font-bold text-primary">
                            <button
                              type="button"
                              onClick={() => setPagoParaComprobante(p)}
                              className="hover:underline cursor-pointer text-left font-mono font-bold text-primary inline-flex items-center gap-1"
                              title="Ver o imprimir comprobante"
                            >
                              {p.comprobante}
                            </button>
                          </td>
                          <td className="py-2.5 px-4 text-on-surface-variant font-mono">
                            {formatFechaHora(p.pagadoEn)}
                          </td>
                          <td className="py-2.5 px-4 font-semibold text-on-surface">
                            {p.pacienteNombre ?? 'Paciente'}
                            {p.pacienteDni && <span className="block text-xs font-mono text-on-surface-variant font-normal">DNI: {p.pacienteDni}</span>}
                          </td>
                          <td className="py-2.5 px-4 text-on-surface">
                            {p.tratamientoNombre ?? 'Abono general odontológico'}
                          </td>
                          <td className="py-2.5 px-4">
                            <span className="font-semibold">
                              {nombreMetodoPago(p.metodoPago)}
                            </span>
                          </td>
                          <td className="py-2.5 px-4 text-right font-mono font-bold text-emerald-700 text-sm">
                            {formatSoles(p.monto)}
                          </td>
                          <td className="py-2.5 px-4">
                            <Badge tono={p.anulado ? 'peligro' : 'primario'}>
                              {p.anulado ? 'Anulado' : 'Válido'}
                            </Badge>
                            {p.anulado && p.motivoAnulacion && (
                              <span className="block text-xs text-error mt-0.5">
                                {p.motivoAnulacion}
                              </span>
                            )}
                          </td>
                          {esAdmin && (
                            <td className="py-2.5 px-4 text-center">
                              {!p.anulado && (
                                <button
                                  type="button"
                                  onClick={() => handleAnularPago(p)}
                                  className="text-error hover:underline text-xs font-medium"
                                >
                                  Anular
                                </button>
                              )}
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {/* ────────────────── PESTAÑA 3: HISTORIAL DE CIERRES ────────────────── */}
      {pestaña === 'historial' && puedeVerResumen && (
        <section className="rounded-card border border-outline bg-surface-container-lowest shadow-card overflow-hidden">
          <div className="p-4 border-b border-outline">
            <h3 className="font-display text-base font-bold text-on-surface">
              Historial de Turnos y Arqueos de Caja
            </h3>
            <p className="text-xs text-on-surface-variant">
              Auditoría de aperturas, cierres y descuadres en {sedeId}
            </p>
          </div>

          {cargandoHistorial ? (
            <div className="p-8 space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : historial.length === 0 ? (
            <div className="p-12 text-center text-xs text-on-surface-variant">
              No hay turnos cerrados en el historial de esta sede.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                  <tr>
                    <th className="py-2.5 px-4">Apertura</th>
                    <th className="py-2.5 px-4">Cierre</th>
                    <th className="py-2.5 px-4">Abierta Por</th>
                    <th className="py-2.5 px-4">Cerrada Por</th>
                    <th className="py-2.5 px-4">Detalle por método</th>
                    <th className="py-2.5 px-4">Observaciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline">
                  {historial.map((ses) => {
                    const dif = Number(ses.diferencia ?? 0)
                    return (
                      <tr key={ses.id} className="hover:bg-surface-container-low/50">
                        <td className="py-2.5 px-4 font-mono text-on-surface-variant">
                          {formatFechaHora(ses.abiertaEn)}
                        </td>
                        <td className="py-2.5 px-4 font-mono text-on-surface-variant">
                          {ses.cerradaEn ? formatFechaHora(ses.cerradaEn) : 'En curso'}
                        </td>
                        <td className="py-2.5 px-4">{ses.abiertaPorNombre ?? 'Usuario'}</td>
                        <td className="py-2.5 px-4">{ses.cerradaPorNombre ?? '-'}</td>
                        <td className="py-2.5 px-4">
                          <details>
                            <summary className="cursor-pointer font-semibold text-primary">Ver cierre por método</summary>
                            <table className="mt-2 min-w-[460px] text-xs">
                              <thead><tr><th className="p-1 text-left">Medio</th><th className="p-1 text-right">Sistema</th><th className="p-1 text-right">Contado</th><th className="p-1 text-right">Diferencia</th></tr></thead>
                              <tbody>
                                {([
                                  ['Efectivo', ses.montoEsperado, ses.montoContado, dif],
                                  ['Yape', ses.montoEsperadoYape, ses.montoContadoYape, ses.diferenciaYape],
                                  ['Plin', ses.montoEsperadoPlin, ses.montoContadoPlin, ses.diferenciaPlin],
                                  ['Tarjetas (débito + crédito)', ses.montoEsperadoTarjeta, ses.montoContadoTarjeta, ses.diferenciaTarjeta],
                                ] as const).map(([medio, esperado, contado, diferencia]) => (
                                  <tr key={medio}>
                                    <th className="p-1 text-left font-medium">{medio}</th>
                                    <td className="p-1 text-right font-mono">{formatSoles(esperado ?? 0)}</td>
                                    <td className="p-1 text-right font-mono">{contado == null ? '—' : formatSoles(contado)}</td>
                                    <td className={`p-1 text-right font-mono ${Math.abs(Number(diferencia ?? 0)) < 0.01 ? 'text-emerald-700' : 'text-error'}`}>
                                      {diferencia == null ? '—' : formatSoles(diferencia)}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </details>
                        </td>
                        <td className="py-2.5 px-4 text-on-surface-variant text-xs">
                          {ses.observaciones || '-'}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {/* ────────────────── MODALES DE CAJA ────────────────── */}
      <AbrirCajaModal
        abierto={modalAbrir}
        alCerrar={() => setModalAbrir(false)}
        sedeId={sedeId}
        sedeNombre={cajaActiva?.sedeNombre}
        alGuardar={() => recargarCaja()}
      />

      {cajaActiva && (
        <CerrarCajaModal
          key={cajaActiva.id ?? 'cierre-caja'}
          abierto={modalCerrar}
          alCerrar={() => setModalCerrar(false)}
          caja={cajaActiva}
          alGuardar={() => recargarCaja()}
        />
      )}

      {cajaActiva && (
        <RegistrarMovimientoModal
          abierto={modalMovimiento}
          alCerrar={() => setModalMovimiento(false)}
          sesionId={cajaActiva.id!}
          alGuardar={() => recargarCaja()}
        />
      )}

      <AnularPagoDialog
        pago={pagoAAnular}
        enviando={anular.isPending}
        error={errorAnulacion}
        alCerrar={() => setPagoAAnular(null)}
        onConfirmar={(m) => void confirmarAnulacion(m)}
      />

      <RegistrarPagoModal
        abierto={modalPago}
        alCerrar={() => setModalPago(false)}
        sedeId={sedeId}
        alGuardar={() => {
          recargarCaja()
          recargarPagos()
        }}
      />

      {pagoParaComprobante && (
        <ImprimirComprobanteModal
          abierto={Boolean(pagoParaComprobante)}
          alCerrar={() => setPagoParaComprobante(null)}
          comprobantes={pagoParaComprobante.comprobante ? [pagoParaComprobante.comprobante] : []}
          tratamiento={{
            id: pagoParaComprobante.tratamientoId ?? pagoParaComprobante.id,
            pacienteId: pagoParaComprobante.pacienteId,
            nombre: pagoParaComprobante.tratamientoNombre ?? 'Atenci?n en Sede',
            costoTotal: pagoParaComprobante.monto,
            descuentoTotal: 0,
            montoPagado: pagoParaComprobante.monto,
            saldoPendiente: 0,
            estado: 'COMPLETADO',
            modalidadPago: pagoParaComprobante.metodoPago === 'EFECTIVO' ? 'AL_CONTADO' : 'TRANSFERENCIA',
            items: [],
          } as TratamientoDTO}
          pacienteNombre={pagoParaComprobante.pacienteNombre}
          mediosDePago={[nombreMetodoPago(pagoParaComprobante.metodoPago)]}
          pacienteDni={pagoParaComprobante.pacienteDni}
          sedeNombre={sesion?.usuario.sedes.find((s) => s.id === sesion.sedeActivaId)?.nombre}
          sedeDireccion={datosSedeComprobante.direccion}
          sedeTelefono={datosSedeComprobante.telefono}
          negocio={sesion?.negocioActivo}
        />
      )}
    </div>
  )
}
