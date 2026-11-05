import { FotoPaciente } from './FotoPaciente'
import { useMemo, useState } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import { Input } from '@/design-system/components/Input'
import { Skeleton } from '@/design-system/components/Skeleton'
import { StatCard } from '@/design-system/components/StatCard'
import { Alert, toast } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { useSedesActivas } from '@/app/agenda/api-agenda'
import { RotuloMostrando } from '@/app/layout/RotuloMostrando'
import { mensajeError } from '@/shared/errores'
import { formatFecha, formatFechaHora, formatSoles } from '@/shared/format'
import { esSaldoAFavor, formatoSaldo } from '@/shared/saldo'
import { ErrorImpresion, imprimirDocumento } from '@/shared/imprimir'
import { esOpcion } from '@/shared/opciones'
import { useAhora } from '@/shared/useAhora'
import { usePacientesQuery } from './api-pacientes'
import {
  construirFilas,
  construirResumen,
  contarPacientesPorEstado,
  fechaHoyLima,
  listarTratamientosDeSedes,
} from './reportePacientes'
import { construirDocumentoReporte } from './reportePacientesDocumento'
import { SEMAFORO_CONFIG, estadoReactivacion, puedeReactivar, usePacientesConTratamientoCompletado } from './reactivacion'
import { RegistrarPacienteModal } from './RegistrarPacienteModal'

type VistaRapida = 'todos' | 'deuda' | 'villamax' | 'cumpleanos' | 'inasistencias' | 'por_completar' | 'inactivos' | 'reactivar'


const FUENTES_ETIQUETA: Record<string, string> = {
  FACEBOOK: 'Facebook Ads',
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
  GOOGLE: 'Google Search',
  REFERIDO_PACIENTE: 'Recomendación',
  REFERIDO_DOCTOR: 'Deriv. Externa',
  LOCAL: 'Pasaba por local',
  OTRO: 'Otro origen',
}

const NOMBRES_MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
]

const FILTROS_FINANZAS = ['TODOS', 'CON_DEUDA', 'AL_DIA'] as const
type FiltroFinanzas = (typeof FILTROS_FINANZAS)[number]

export function PacientesPage() {
  const sesion = useSesion()
  const navigate = useNavigate()
  const ahora = useAhora()
  const { data: sedes = [] } = useSedesActivas()
  const [busqueda, setBusqueda] = useState('')
  const [filtroFuente, setFiltroFuente] = useState<string>('TODAS')
  const [filtroFinanzas, setFiltroFinanzas] = useState<FiltroFinanzas>('TODOS')
  const [vistaRapida, setVistaRapida] = useState<VistaRapida>('todos')
  const [modalRegistroAbierto, setModalRegistroAbierto] = useState(false)
  const [exportando, setExportando] = useState(false)
  const [errorReporte, setErrorReporte] = useState<string | null>(null)
  const conTratamientoCompletado = usePacientesConTratamientoCompletado(
    (sesion?.usuario.sedes ?? []).filter((s) => s.marca === sesion?.negocioActivo).map((s) => s.id),
  )

  // Consulta al backend: acotada a la sede del panel o todas las del negocio
  const sedeIdQuery = sesion?.sedeActivaId ?? undefined
  const { data: pacientes = [], isLoading, isError, refetch } = usePacientesQuery({
    busqueda: busqueda.trim() || undefined,
    sedeId: sedeIdQuery,
  })

  const mesActualNombre = NOMBRES_MESES[new Date().getMonth()]

  // Métricas calculadas sobre la lista real de la base de datos
  const metricas = useMemo(() => {
    const total = pacientes.length
    const conDeuda = pacientes.filter((p) => p.tieneDeuda)
    const totalDeudaMonto = conDeuda.reduce((acc, p) => acc + (p.saldoPendiente || 0), 0)
    const cumpleaneros = pacientes.filter((p) => p.esCumpleaneroMes)
    const villamaxPacientes = pacientes.filter(
      (p) => p.sedeId === 'sede-villamax-chilca' || p.fuenteCaptacion === 'REFERIDO_DOCTOR'
    )
    const inasistencias = pacientes.filter((p) => (p.totalInasistencias ?? 0) > 0)

    return {
      total,
      conDeudaCount: conDeuda.length,
      totalDeudaMonto,
      cumpleanerosCount: cumpleaneros.length,
      villamaxCount: villamaxPacientes.length,
      inasistenciasCount: inasistencias.length,
    }
  }, [pacientes])

  // Filtrado en memoria para las combinaciones de vista rápida y selects
  const pacientesFiltrados = useMemo(() => {
    return pacientes.filter((p) => {
      // Filtro por fuente
      if (filtroFuente !== 'TODAS' && p.fuenteCaptacion !== filtroFuente) return false

      // Filtro por finanzas
      if (filtroFinanzas === 'CON_DEUDA' && !p.tieneDeuda) return false
      if (filtroFinanzas === 'AL_DIA' && p.tieneDeuda) return false

      // Vistas rápidas
      if (vistaRapida === 'deuda' && !p.tieneDeuda) return false
      if (vistaRapida === 'cumpleanos' && !p.esCumpleaneroMes) return false
      if (vistaRapida === 'inasistencias' && (p.totalInasistencias ?? 0) === 0) return false
      if (vistaRapida === 'villamax') {
        const esVillamax = p.sedeId === 'sede-villamax-chilca' || p.fuenteCaptacion === 'REFERIDO_DOCTOR'
        if (!esVillamax) return false
      }
      if (vistaRapida === 'reactivar') {
        if (!puedeReactivar(p, conTratamientoCompletado, ahora)) return false
      }
      if (vistaRapida === 'inactivos') {
        // Solo quienes ya vinieron y dejaron de venir: un paciente nuevo no es "inactivo".
        const estado = estadoReactivacion(p.ultimaCitaAtendida)
        if (estado !== 'seguimiento' && estado !== 'inactivo') return false
      }
      if (vistaRapida === 'por_completar') {
        const faltaDni = !p.dni || p.dni.trim() === ''
        const faltaTelefono = !p.telefono || p.telefono.trim() === ''
        if (!faltaDni && !faltaTelefono) return false
      }

      return true
    })
  }, [pacientes, filtroFuente, filtroFinanzas, vistaRapida, conTratamientoCompletado, ahora])

  const conteoReactivables = useMemo(
    () => pacientes.filter((p) => puedeReactivar(p, conTratamientoCompletado, ahora)).length,
    [pacientes, conTratamientoCompletado, ahora]
  )

  const conteoInactivos = useMemo(
    () => pacientes.filter((p) => ['seguimiento', 'inactivo'].includes(estadoReactivacion(p.ultimaCitaAtendida))).length,
    [pacientes]
  )

  const conteoPorCompletar = useMemo(
    () => pacientes.filter((p) => !p.dni || !p.telefono).length,
    [pacientes]
  )

  /**
   * Reporte formal en PDF de los pacientes que se ven en la lista: membrete, sede activa, fecha de emisión en hora de
   * Lima, resumen clínico, ranking de tratamientos y tabla de pacientes. Se imprime desde un marco oculto: en el diálogo
   * de impresión del navegador se elige "Guardar como PDF".
   */
  async function exportarReporte() {
    setErrorReporte(null)
    setExportando(true)
    try {
      const sedeIds = (sesion?.usuario.sedes ?? []).map((s) => s.id)
      const tratamientos = await listarTratamientosDeSedes(sedeIds)
      const nombreSede = (id?: string) => sedes.find((s) => s.id === id)?.nombre ?? sesion?.usuario.sedes.find((s) => s.id === id)?.nombre ?? id ?? ''
      await imprimirDocumento(
        construirDocumentoReporte({
          sede: nombreSede(sesion?.sedeActivaId ?? undefined) || 'Todas las sedes',
          emitidoEn: formatFechaHora(new Date()),
          fecha: fechaHoyLima(),
          pacientes: contarPacientesPorEstado(pacientesFiltrados, tratamientos),
          resumen: construirResumen(tratamientos),
          filas: construirFilas(pacientesFiltrados, tratamientos, nombreSede),
        }),
      )
      toast(`Reporte listo para guardar como PDF: ${pacientesFiltrados.length} pacientes`)
    } catch (error) {
      setErrorReporte(error instanceof ErrorImpresion ? error.message : mensajeError(error))
    } finally {
      setExportando(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Encabezado Principal Stitch B6 */}
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="font-display text-2xl font-bold tracking-tight text-on-surface sm:text-3xl">
              Directorio de Pacientes
            </h1>
            <Badge tono="villamax">Junín Sede-Network</Badge>
            <RotuloMostrando />
          </div>
          <p className="mt-1 text-sm text-on-surface-variant max-w-2xl">
            Gestión clínica unificada, control de cuentas por cobrar y trazabilidad inter-sede para
            Villadent y Centro Tomográfico Villamax 3D.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Button variant="secundario" onClick={() => void exportarReporte()} cargando={exportando} className="border border-outline">
            <svg className="size-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Exportar reporte
          </Button>

          <Button
            variant="accion"
            onClick={() => setModalRegistroAbierto(true)}
            className="shadow-sm"
          >
            <svg className="size-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            + Registrar nuevo paciente
          </Button>
        </div>
      </header>

      {errorReporte && (
        <Alert tono="error" titulo="No se pudo exportar el reporte" onCerrar={() => setErrorReporte(null)}>
          {errorReporte}
        </Alert>
      )}

      {/* 4 StatCards Métricas del Directorio */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          etiqueta="POBLACIÓN TOTAL"
          valor={metricas.total.toLocaleString()}
          pie="100% historias digitalizadas DNI"
          icono={
            <svg className="size-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          }
          cargando={isLoading}
        />

        <StatCard
          etiqueta="NUEVOS ESTE MES"
          valor={`+${pacientesFiltrados.length}`}
          pie="Trazabilidad inter-sede activa"
          icono={
            <svg className="size-6 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
            </svg>
          }
          tonoIcono="primario"
          cargando={isLoading}
        />

        <StatCard
          etiqueta="CUENTAS POR COBRAR"
          valor={`${metricas.conDeudaCount} pacientes`}
          pie={`${formatSoles(metricas.totalDeudaMonto)} pendiente`}
          icono={
            <svg className="size-6 text-secondary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
          }
          tonoIcono="accion"
          cargando={isLoading}
        />

        <StatCard
          etiqueta={`CUMPLEAÑOS DE ${mesActualNombre.toUpperCase()}`}
          valor={metricas.cumpleanerosCount}
          pie="Promociones Ley 29733 disponibles"
          icono={
            <svg className="size-6 text-tertiary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V6a2 2 0 10-2 2h2m0 0a2 2 0 100 4 2 2 0 000-4z" />
            </svg>
          }
          tonoIcono="villamax"
          cargando={isLoading}
        />
      </div>

      {/* Barra de Filtros y Vistas Rápidas */}
      <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div className="relative">
            <Input
              id="buscador-pacientes"
              type="search"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar por nombre, DNI (8 dígitos)..."
              className="pr-16"
            />
            <span className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded bg-surface-container px-1.5 py-0.5 text-xs font-mono text-on-surface-variant">
              Enter
            </span>
          </div>

          <div>
            <select
              id="filtro-fuente-captacion"
              value={filtroFuente}
              onChange={(e) => setFiltroFuente(e.target.value)}
              className="w-full rounded-control border border-outline-variant/60 bg-surface-container-low/80 px-3.5 py-2 text-sm text-on-surface focus:border-primary focus:outline-none"
            >
              <option value="TODAS">Origen: Todas las fuentes</option>
              <option value="FACEBOOK">Facebook</option>
              <option value="INSTAGRAM">Instagram</option>
              <option value="TIKTOK">TikTok</option>
              <option value="GOOGLE">Google Search</option>
              <option value="REFERIDO_PACIENTE">Recomendación (Referido)</option>
              <option value="REFERIDO_DOCTOR">Derivación Médica Externa</option>
              <option value="LOCAL">Pasaba por el local</option>
              <option value="OTRO">Otro</option>
            </select>
          </div>

          <div>
            <select
              id="filtro-finanzas"
              value={filtroFinanzas}
              onChange={(e) => {
                if (esOpcion(FILTROS_FINANZAS, e.target.value)) setFiltroFinanzas(e.target.value)
              }}
              className="w-full rounded-control border border-outline-variant/60 bg-surface-container-low/80 px-3.5 py-2 text-sm text-on-surface focus:border-primary focus:outline-none"
            >
              <option value="TODOS">Finanzas: Todos los estados</option>
              <option value="CON_DEUDA">Con deuda pendiente</option>
              <option value="AL_DIA">Al día (Sin deuda)</option>
            </select>
          </div>
        </div>

        {/* Vistas rápidas (Pills) */}
        <div className="flex flex-wrap items-center gap-2 pt-3 border-t border-outline-variant/30">
          <span className="text-xs font-medium uppercase tracking-wider text-on-surface-variant mr-1">
            Vistas rápidas:
          </span>

          <button
            type="button"
            onClick={() => setVistaRapida('todos')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'todos'
                ? 'bg-acento text-on-acento'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Todos ({metricas.total})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('deuda')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'deuda'
                ? 'bg-error text-on-error'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            • Con Deuda ({metricas.conDeudaCount})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('villamax')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'villamax'
                ? 'bg-tertiary text-on-tertiary'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Pacientes Villamax Tomografía ({metricas.villamaxCount})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('cumpleanos')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'cumpleanos'
                ? 'bg-secondary text-on-secondary'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Cumpleaños este mes ({metricas.cumpleanerosCount})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('inasistencias')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'inasistencias'
                ? 'bg-outline text-on-surface'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Con Inasistencias previas ({metricas.inasistenciasCount})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('inactivos')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'inactivos'
                ? 'bg-amber-600 text-white'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            ● En Seguimiento / Inactivos ({conteoInactivos})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('reactivar')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'reactivar'
                ? 'bg-emerald-600 text-white'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            ● Por Reactivar &gt; 6 meses ({conteoReactivables})
          </button>

          <button
            type="button"
            onClick={() => setVistaRapida('por_completar')}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              vistaRapida === 'por_completar'
                ? 'bg-secondary text-on-secondary'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Por Completar ({conteoPorCompletar})
          </button>
        </div>
      </section>

      {/* Tabla Interactiva Stitch B6 */}
      <section className="overflow-hidden rounded-card border border-outline-variant/40 bg-surface-container-lowest shadow-card">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-on-surface">
            <thead className="border-b border-outline-variant/40 bg-surface-container-low/75 text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
              <tr>
                <th scope="col" className="px-4 py-3 sm:px-6">Paciente &amp; Historial</th>
                <th scope="col" className="px-4 py-3">DNI / Nº HC</th>
                <th scope="col" className="px-4 py-3">Contacto / WhatsApp</th>
                <th scope="col" className="px-4 py-3">Sede &amp; Origen</th>
                <th scope="col" className="px-4 py-3">Última Atención</th>
                <th scope="col" className="px-4 py-3">Saldo / Cuenta</th>
                <th scope="col" className="px-4 py-3 text-right">Acción</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-outline-variant/30">
              {isLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="animate-pulse">
                    <td className="px-4 py-4 sm:px-6"><Skeleton className="h-5 w-48" /></td>
                    <td className="px-4 py-4"><Skeleton className="h-4 w-24" /></td>
                    <td className="px-4 py-4"><Skeleton className="h-4 w-28" /></td>
                    <td className="px-4 py-4"><Skeleton className="h-4 w-32" /></td>
                    <td className="px-4 py-4"><Skeleton className="h-4 w-24" /></td>
                    <td className="px-4 py-4"><Skeleton className="h-4 w-20" /></td>
                    <td className="px-4 py-4 text-right"><Skeleton className="h-8 w-20 ml-auto" /></td>
                  </tr>
                ))
              ) : isError ? (
                <tr>
                  <td colSpan={7} className="p-8 text-center text-error">
                    No se pudo cargar la lista de pacientes.
                    <Button variant="secundario" onClick={() => refetch()} className="ml-3">
                      Reintentar
                    </Button>
                  </td>
                </tr>
              ) : pacientesFiltrados.length === 0 ? (
                <tr>
                  <td colSpan={7} className="p-8 text-center text-on-surface-variant">
                    No se encontraron pacientes registrados con los filtros aplicados.
                  </td>
                </tr>
              ) : (
                pacientesFiltrados.map((p) => {
                  const esVillamax = p.sedeId === 'sede-villamax-chilca' || p.fuenteCaptacion === 'REFERIDO_DOCTOR'

                  const semaforo = SEMAFORO_CONFIG[estadoReactivacion(p.ultimaCitaAtendida)]
                  const mostrarReactivar = puedeReactivar(p, conTratamientoCompletado)
                  const diasSinVisita = p.ultimaCitaAtendida
                    ? Math.floor((ahora - new Date(p.ultimaCitaAtendida).getTime()) / 86_400_000)
                    : null
                  const mesesSinVisita = diasSinVisita !== null ? Math.floor(diasSinVisita / 30) : null
                  const nombreSede = p.sedeId === 'sede-chilca' || p.sedeId === 'sede-chilca-1'
                    ? 'Villadent Chilca'
                    : p.sedeId === 'sede-huancayo-centro' || p.sedeId === 'sede-huancayo-1'
                    ? 'Huancayo Centro'
                    : p.sedeId === 'sede-villamax-chilca'
                    ? 'Villamax Chilca'
                    : (p.sedeId ?? 'Clínica')
                  const mensajeReactivacion = encodeURIComponent(
                    `Hola ${p.nombres ?? ''}, le saludamos de ${nombreSede}. Notamos que han pasado más de ${mesesSinVisita} meses desde su última limpieza/control dental. ¿Desea que le agendemos su chequeo preventivo esta semana?`
                  )

                  return (
                    <tr
                      key={p.id}
                      className="transition-colors hover:bg-surface-container-low/50"
                    >
                      {/* Paciente y badges */}
                      <td className="px-4 py-4 sm:px-6">
                        <div className="flex items-start gap-3">
                          <FotoPaciente pacienteId={p.id ?? ''} nombres={p.nombres} apellidos={p.apellidos} fotoActualizadaEn={p.fotoActualizadaEn} tamano="sm" />
                          <div>
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <Link
                                to="/app/pacientes/$id"
                                params={{ id: p.id ?? '' }}
                                className="font-semibold text-on-surface hover:text-primary transition-colors hover:underline"
                              >
                                {p.nombreCompleto}
                              </Link>
                              {esVillamax && (
                                <Badge tono="villamax" className="text-xs py-0 px-1.5">
                                  Villamax 3D
                                </Badge>
                              )}
                              {p.avisoInasistencias && (
                                <Badge tono="peligro" className="text-xs py-0 px-1.5">
                                  {p.totalInasistencias} inasistencias
                                </Badge>
                              )}
                              {p.esCumpleaneroMes && (
                                <Badge tono="accion" className="text-xs py-0 px-1.5">
                                  Cumpleañero
                                </Badge>
                              )}
                              <span className={`inline-flex items-center rounded-full px-1.5 py-0 text-xs font-medium ${semaforo.clase}`}>
                                {semaforo.etiqueta}
                              </span>
                            </div>
                            <p className="text-xs text-on-surface-variant mt-0.5">
                              {(p.edad ?? 0) > 0 ? `${p.edad} años` : 'Edad no reg.'}
                              {p.gestante ? ' · Gestante' : ''}
                              {p.antecedentesMedicos ? ` · ${p.antecedentesMedicos}` : ''}
                            </p>
                          </div>
                        </div>
                      </td>

                      {/* DNI y HC */}
                      <td className="px-4 py-4 font-mono text-xs">
                        <span className="font-bold text-on-surface">{p.dni}</span>
                        <p className="text-xs text-on-surface-variant">{p.numeroHistoriaClinica ?? `HC-${p.dni}`}</p>
                      </td>

                      {/* WhatsApp / Teléfono */}
                      <td className="px-4 py-4">
                        {p.telefono ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-xs font-mono">{p.telefono}</span>
                            <a
                              href={`https://wa.me/51${p.telefono}?text=Hola%20${encodeURIComponent(p.nombres ?? '')}%2C%20le%20saludamos%20de%20Villadent%20/%20Villamax.`}
                              target="_blank"
                              rel="noreferrer"
                              title="Escribir por WhatsApp"
                              className="inline-flex size-6 items-center justify-center rounded-full bg-emerald-600 text-white hover:bg-emerald-700 transition-colors"
                            >
                              <svg className="size-3.5 fill-current" viewBox="0 0 24 24">
                                <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946.003-6.556 5.338-11.891 11.893-11.891 3.181.001 6.167 1.24 8.413 3.488 2.245 2.248 3.481 5.236 3.48 8.414-.003 6.557-5.338 11.892-11.893 11.892-1.99-.001-3.951-.5-5.688-1.448l-6.305 1.654zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.592 5.448 0 9.886-4.434 9.889-9.885.002-5.462-4.415-9.89-9.881-9.892-5.452 0-9.887 4.434-9.889 9.884-.001 2.225.651 3.891 1.746 5.634l-.999 3.648 3.742-.981z" />
                              </svg>
                            </a>
                          </div>
                        ) : (
                          <span className="text-xs text-on-surface-variant">—</span>
                        )}
                      </td>

                      {/* Sede y Origen */}
                      <td className="px-4 py-4">
                        <p className="text-xs font-medium text-on-surface">
                          {p.sedeId === 'sede-chilca' || p.sedeId === 'sede-chilca-1'
                            ? 'Villadent Chilca'
                            : p.sedeId === 'sede-huancayo-centro' || p.sedeId === 'sede-huancayo-1'
                            ? 'Huancayo Centro'
                            : p.sedeId === 'sede-villamax-chilca'
                            ? 'Villamax Chilca'
                            : p.sedeId}
                        </p>
                        <Badge tono="neutro" className="mt-0.5 text-xs py-0">
                          {FUENTES_ETIQUETA[p.fuenteCaptacion ?? ''] ?? p.fuenteCaptacion ?? 'Directo'}
                        </Badge>
                      </td>

                      {/* Última Atención */}
                      <td className="px-4 py-4 text-xs text-on-surface">
                        {p.ultimaCitaAtendida ? formatFecha(p.ultimaCitaAtendida) : 'Sin atenciones'}
                      </td>

                      {/* Saldo / Cuenta */}
                      <td className="px-4 py-4">
                        {(p.saldoPendiente ?? 0) > 0 ? (
                          <div className="text-xs">
                            <span className="font-semibold text-error">
                              Deuda: {formatoSaldo(p.saldoPendiente)}
                            </span>
                          </div>
                        ) : esSaldoAFavor(p.saldoPendiente) ? (
                          <span className="text-xs font-semibold text-emerald-700">{formatoSaldo(p.saldoPendiente)}</span>
                        ) : (
                          <span className="text-xs font-medium text-emerald-700">
                            Al día ({formatSoles(0)})
                          </span>
                        )}
                      </td>

                      {/* Acción Ver Ficha + Reactivar */}
                      <td className="px-4 py-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {mostrarReactivar && p.telefono && (
                            <a
                              href={`https://wa.me/51${p.telefono}?text=${mensajeReactivacion}`}
                              target="_blank"
                              rel="noreferrer"
                              title="Reactivar por WhatsApp"
                            >
                              <Button
                                variant="secundario"
                                className="text-xs py-1 px-2 h-auto border-red-300 text-red-700 hover:bg-red-50"
                              >
                                Reactivar
                              </Button>
                            </a>
                          )}
                          <Link
                            to="/app/pacientes/$id"
                            params={{ id: p.id ?? '' }}
                          >
                            <Button variant="secundario" className="text-xs py-1 px-2.5 h-auto">
                              Ver Ficha
                            </Button>
                          </Link>
                        </div>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Footer de Paginación Stitch B6 */}
        <div className="flex flex-col gap-3 border-t border-outline px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6 bg-surface-container-low/30 text-xs text-on-surface-variant">
          <p>
            Mostrando <strong>{pacientesFiltrados.length}</strong> de{' '}
            <strong>{metricas.total}</strong> pacientes registrados
          </p>

          <div className="flex items-center gap-1.5">
            <span className="text-xs">Centro Odontológico Multisede · Villadent &amp; Villamax 3D</span>
          </div>
        </div>
      </section>

      {/* Banner inferior de Integración Villamax */}
      <footer className="flex flex-col gap-2 rounded-card border border-outline bg-surface-container-low p-4 sm:flex-row sm:items-center sm:justify-between text-xs text-on-surface-variant">
        <div className="flex items-center gap-2">
          <svg className="size-4 text-tertiary shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <span>
            Integración en tiempo real con Villamax Radiología Maxilofacial 3D (Chilca). Acceso directo a
            archivos DICOM y periapicales vinculados al DNI del paciente.
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="size-2 rounded-full bg-emerald-600 inline-block" />
          <span>Sincronización Clínica Activa</span>
        </div>
      </footer>

      {/* Modal de Registro */}
      <RegistrarPacienteModal
        abierto={modalRegistroAbierto}
        alCerrar={() => setModalRegistroAbierto(false)}
        alGuardar={(nuevo) => {
          void refetch()
          // Recién registrado: directo a su ficha de ingreso, sin buscarlo de nuevo.
          if (nuevo.id) void navigate({ to: '/app/pacientes/$id', params: { id: nuevo.id }, search: { tab: 'ficha' } })
        }}
      />
    </div>
  )
}
