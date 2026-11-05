import { imprimirDocumento } from '@/shared/imprimir'
import { CierreDeMes } from './CierreDeMes'
import { documentoReporteGerencial } from './documentoReporteGerencial'
import { useState, useMemo } from 'react'
import {
  LayoutDashboard,
  DollarSign,
  CalendarCheck,
  Users,
  Stethoscope,
  Boxes,
  FileBarChart2,
  ScanLine,
  Target,
} from 'lucide-react'
import { useSesion } from '@/shared/auth'
import { RotuloMostrando } from '@/app/layout/RotuloMostrando'
import { SinPermisoPage } from '@/app/comun/SinPermisoPage'
import { ErrorState } from '@/design-system/components/ErrorState'
import { Skeleton } from '@/design-system/components/Skeleton'
import { FiltrosReportesBar, type RangoPreset } from './FiltrosReportesBar'
import { DashboardGeneralTab } from './DashboardGeneralTab'
import { FinanzasTab } from './FinanzasTab'
import { CitasTab } from './CitasTab'
import { PacientesTab } from './PacientesTab'
import { DoctoresTab } from './DoctoresTab'
import { RendimientoTab } from './RendimientoTab'
import { VillamaxTab } from './VillamaxTab'
import { InventarioTab } from './InventarioTab'
import {
  useReporteFinanciero,
  useReporteCitas,
  useReportePacientes,
  useReporteDoctores,
  useReporteInventario,
  useReporteDeudores,
} from './api-estadisticas'
import type { FiltrosReportes } from './types'

type PestañaReportes =
  | 'dashboard'
  | 'finanzas'
  | 'citas'
  | 'pacientes'
  | 'doctores'
  | 'metas'
  | 'villamax'
  | 'inventario'

function formatearFechaISO(d: Date): string {
  const anio = d.getFullYear()
  const mes = String(d.getMonth() + 1).padStart(2, '0')
  const dia = String(d.getDate()).padStart(2, '0')
  return `${anio}-${mes}-${dia}`
}

export function EstadisticasPage() {
  const sesion = useSesion()
  const rol = sesion?.usuario.rol ?? 'RECEPCION'

  // Validar permisos: solo ADMIN_GENERAL y ADMIN_SEDE
  if (rol !== 'ADMIN_GENERAL' && rol !== 'ADMIN_SEDE') {
    return <SinPermisoPage />
  }

  return <EstadisticasContenido />
}

function EstadisticasContenido() {
  const sesion = useSesion()
  const rol = sesion?.usuario.rol ?? 'RECEPCION'
  const esAdminGeneral = rol === 'ADMIN_GENERAL'
  const esVillamax = sesion?.negocioActivo === 'VILLAMAX'

  const [pestaña, setPestaña] = useState<PestañaReportes>('dashboard')
  const [presetRango, setPresetRango] = useState<RangoPreset>('esteMes')

  // Fechas iniciales por defecto: este mes
  const hoy = useMemo(() => new Date(), [])
  const fechaHoyISO = useMemo(() => formatearFechaISO(hoy), [hoy])
  const primerDiaMesISO = useMemo(() => {
    const d = new Date(hoy.getFullYear(), hoy.getMonth(), 1)
    return formatearFechaISO(d)
  }, [hoy])

  const [filtros, setFiltros] = useState<FiltrosReportes>({
    sedeId: esAdminGeneral ? undefined : (sesion?.sedeActivaId ?? undefined),
    desde: primerDiaMesISO,
    hasta: fechaHoyISO,
    unidadNegocio: 'todas',
  })

  // Manejador de cambio de presets de fechas
  const cambiarPreset = (nuevoPreset: RangoPreset) => {
    setPresetRango(nuevoPreset)
    const hoyActual = new Date()

    if (nuevoPreset === 'hoy') {
      const hoyStr = formatearFechaISO(hoyActual)
      setFiltros((prev) => ({ ...prev, desde: hoyStr, hasta: hoyStr }))
    } else if (nuevoPreset === '7dias') {
      const hace7 = new Date()
      hace7.setDate(hoyActual.getDate() - 6)
      setFiltros((prev) => ({
        ...prev,
        desde: formatearFechaISO(hace7),
        hasta: formatearFechaISO(hoyActual),
      }))
    } else if (nuevoPreset === 'esteMes') {
      const primero = new Date(hoyActual.getFullYear(), hoyActual.getMonth(), 1)
      setFiltros((prev) => ({
        ...prev,
        desde: formatearFechaISO(primero),
        hasta: formatearFechaISO(hoyActual),
      }))
    } else if (nuevoPreset === 'mesAnterior') {
      const primeroAnt = new Date(hoyActual.getFullYear(), hoyActual.getMonth() - 1, 1)
      const ultimoAnt = new Date(hoyActual.getFullYear(), hoyActual.getMonth(), 0)
      setFiltros((prev) => ({
        ...prev,
        desde: formatearFechaISO(primeroAnt),
        hasta: formatearFechaISO(ultimoAnt),
      }))
    }
  }

  const actualizarFiltros = (parcial: Partial<FiltrosReportes>) => {
    setFiltros((prev) => ({ ...prev, ...parcial }))
  }

  const filtrosEfectivos: FiltrosReportes = useMemo(() => ({
    ...filtros,
    sedeId: sesion?.sedeActivaId ?? undefined,
    unidadNegocio: esVillamax ? 'radiologia' : 'clinica',
  }), [filtros, sesion?.sedeActivaId, esVillamax])

  // Consultas React Query
  const qFinanciero = useReporteFinanciero(filtrosEfectivos)
  const qDeudores = useReporteDeudores(filtrosEfectivos.sedeId)
  const qCitas = useReporteCitas(filtrosEfectivos)
  const qPacientes = useReportePacientes(filtrosEfectivos)
  const qDoctores = useReporteDoctores(filtrosEfectivos)
  const qInventario = useReporteInventario(filtrosEfectivos.sedeId)

  const cargando =
    qFinanciero.isLoading ||
    qCitas.isLoading ||
    qPacientes.isLoading ||
    qDoctores.isLoading ||
    qInventario.isLoading

  const hayError =
    qFinanciero.isError ||
    qCitas.isError ||
    qPacientes.isError ||
    qDoctores.isError ||
    qInventario.isError

  const refetchTodo = () => {
    qFinanciero.refetch()
    qDeudores.refetch()
    qCitas.refetch()
    qPacientes.refetch()
    qDoctores.refetch()
    qInventario.refetch()
  }

  // Reporte propio para imprimir o guardar en PDF: resumen en palabras, cada cifra explicada y tablas ordenadas.
  const imprimir = () => {
    const sedeNombre = qFinanciero.data?.sedeNombre ?? sesion?.usuario.sedes.find((s) => s.id === sesion.sedeActivaId)?.nombre ?? 'Todas las sedes'
    void imprimirDocumento(
      documentoReporteGerencial({
        negocio: esVillamax ? 'VILLAMAX' : 'VILLADENT',
        sedeNombre,
        desde: filtros.desde,
        hasta: filtros.hasta,
        financiero: qFinanciero.data,
        citas: qCitas.data,
        pacientes: qPacientes.data,
        doctores: qDoctores.data,
        inventario: qInventario.data,
        deudores: qDeudores.data,
      }),
    ).catch(() => window.print())
  }

  return (
    <div className="flex flex-col gap-6 p-4 md:p-8 max-w-7xl mx-auto w-full">
      {/* Encabezado Ejecutivo */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-panel bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300">
              <FileBarChart2 className="size-5" />
            </span>
            <h1 className="font-display text-2xl font-bold tracking-tight text-on-surface">
              Panel Gerencial y Estadísticas
            </h1>
          </div>
          <p className="mt-1 text-xs text-on-surface-variant">
            Métricas clínicas, operativas y financieras consolidadas de OdontoMax · Villadent & Villamax
          </p>
        </div>

        <div className="flex items-center gap-2 print:hidden">
          <CierreDeMes />
          <RotuloMostrando />
        </div>
      </div>

      {/* Barra de Filtros Superiores */}
      <FiltrosReportesBar
        filtros={filtros}
        presetRango={presetRango}
        onChangePreset={cambiarPreset}
        onChangeFiltros={actualizarFiltros}
        onRefrescar={refetchTodo}
        onImprimir={imprimir}
        cargando={cargando}
        esAdminGeneral={esAdminGeneral}
      />

      {/* Manejo de Estados de Error */}
      {hayError && (
        <ErrorState
          titulo="Error al cargar los reportes gerenciales"
          descripcion="No fue posible sincronizar todas las estadísticas con el servidor. Verifica tu conexión e intenta de nuevo."
          onReintentar={refetchTodo}
        />
      )}

      {/* Selector de Pestañas */}
      <div className="flex flex-wrap gap-1.5 border-b border-outline-variant/30 pb-2 print:hidden">
        <button
          type="button"
          onClick={() => setPestaña('dashboard')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'dashboard'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <LayoutDashboard className="size-4" />
          Dashboard General
        </button>

        <button
          type="button"
          onClick={() => setPestaña('finanzas')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'finanzas'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <DollarSign className="size-4" />
          Finanzas y Caja
        </button>

        <button
          type="button"
          onClick={() => setPestaña('citas')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'citas'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <CalendarCheck className="size-4" />
          Agenda y Citas
        </button>

        <button
          type="button"
          onClick={() => setPestaña('pacientes')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'pacientes'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <Users className="size-4" />
          Pacientes y Captación
        </button>

        <button
          type="button"
          onClick={() => setPestaña('doctores')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'doctores'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <Stethoscope className="size-4" />
          Rendimiento Doctores
        </button>

        <button
          type="button"
          onClick={() => setPestaña('metas')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'metas'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <Target className="size-4" />
          Metas y Tiempos
        </button>

        {esVillamax && (
          <button
            type="button"
            onClick={() => setPestaña('villamax')}
            className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
              pestaña === 'villamax'
                ? 'bg-acento text-on-acento shadow-sm'
                : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
            }`}
          >
            <ScanLine className="size-4" />
            Resumen Villamax
          </button>
        )}

        <button
          type="button"
          onClick={() => setPestaña('inventario')}
          className={`flex items-center gap-1.5 rounded-panel px-3.5 py-2 text-xs font-semibold transition-all ${
            pestaña === 'inventario'
              ? 'bg-acento text-on-acento shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
          }`}
        >
          <Boxes className="size-4" />
          Almacén e Inventario
        </button>
      </div>

      {/* Skeletons durante la primera carga */}
      {cargando && !qFinanciero.data && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5 py-4">
          <Skeleton className="h-32 rounded-card" />
          <Skeleton className="h-32 rounded-card" />
          <Skeleton className="h-32 rounded-card" />
          <Skeleton className="h-32 rounded-card" />
          <Skeleton className="h-32 rounded-card" />
        </div>
      )}

      {/* Contenido de la pestaña activa */}
      {(!cargando || qFinanciero.data) && (
        <>
          {pestaña === 'dashboard' && (
            <DashboardGeneralTab
              financiero={qFinanciero.data}
              citas={qCitas.data}
              pacientes={qPacientes.data}
              inventario={qInventario.data}
              deudores={qDeudores.data}
              cargando={cargando}
            />
          )}

          {pestaña === 'finanzas' && (
            <FinanzasTab
              financiero={qFinanciero.data}
              deudores={qDeudores.data}
              cargando={cargando}
            />
          )}

          {pestaña === 'citas' && (
            <CitasTab citas={qCitas.data} cargando={cargando} />
          )}

          {pestaña === 'pacientes' && (
            <PacientesTab pacientes={qPacientes.data} cargando={cargando} />
          )}

          {pestaña === 'doctores' && (
            <DoctoresTab doctores={qDoctores.data} cargando={cargando} />
          )}

          {pestaña === 'metas' && <RendimientoTab />}

          {pestaña === 'villamax' && esVillamax && (
            <VillamaxTab financiero={qFinanciero.data} desde={filtros.desde ?? primerDiaMesISO} hasta={filtros.hasta ?? fechaHoyISO} cargando={cargando} />
          )}

          {pestaña === 'inventario' && (
            <InventarioTab inventario={qInventario.data} cargando={cargando} />
          )}
        </>
      )}
    </div>
  )
}
