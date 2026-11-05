import { nombreMetodoPago } from '@/shared/metodosPago'
import { CronogramaCuotas } from '../tratamientos/CronogramaCuotas'
import { useState, useEffect } from 'react'
import { Link, useParams, useSearch } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Alert, ErrorState, buttonVariants, toast } from '@/design-system'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import { Skeleton } from '@/design-system/components/Skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/design-system/components/Tabs'
import { formatFecha, formatSoles, formatFechaHora } from '@/shared/format'
import { esSaldoAFavor, formatoSaldo } from '@/shared/saldo'
import { usePacienteQuery } from './api-pacientes'
import { FichasPorTratamiento } from '../clinica/FichasPorTratamiento'
import { useDatosSede } from '../caja/useDatosSede'
import { AccionesAdminPaciente } from './AccionesAdminPaciente'
import { cambiosDe, useSolicitudesDePaciente } from './api-correcciones'
import { SolicitarCorreccionModal } from './SolicitarCorreccionModal'
import {
  useTratamientosPaciente,
  useAceptarTratamientoMutation,
  useActualizarTratamientoMutation,
  type TratamientoDTO,
} from '../tratamientos/api-tratamientos'
import { esSinPermiso, mensajeError } from '@/shared/errores'
import { FileText, LockKeyhole, Phone, Wallet } from 'lucide-react'
import { puede, useSesion, obtenerToken } from '@/shared/auth'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { NuevoTratamientoModal } from '../tratamientos/NuevoTratamientoModal'
import { usePagosPaciente } from '../caja/api-caja'
import { RegistrarPagoModal } from '../caja/RegistrarPagoModal'
import {
  MarcoResumen,
  TarjetaEstudios,
  TarjetaProximaCita,
  TarjetaTratamientoActivo,
} from './TarjetasResumen'
import { estadoDe } from './estadoCarga'
import { FichaIngresoPanel } from './FichaIngresoPanel'
import { useDescuentosRecomendacion } from './api-descuentos-recomendacion'
import { PresupuestosPaciente } from '../tratamientos/PresupuestosPaciente'
import { CambiarSesionesPlan } from '../tratamientos/CambiarSesionesPlan'
import { FotoPaciente } from './FotoPaciente'

/* ─────────── Importaciones de Clínica (Fase 5) ─────────── */
import { useNotasEvolucion, useAccesosHistoria, registrarAperturaHistoria } from '../clinica/api-historia'
import { useOdontogramaActual } from '../clinica/api-odontograma'
import { useRadiografiasPaciente, resolverUrlRadiografia, type RadiografiaDTO } from '../clinica/api-radiografias'
import { useConsentimientosPaciente, type ConsentimientoDTO } from '../clinica/api-consentimientos'
import { EpisodiosOdontograma } from '../clinica/EpisodiosOdontograma'
import { OdontogramaVisual } from '../clinica/OdontogramaVisual'
import { EditarPiezaOdontogramaModal } from '../clinica/EditarPiezaOdontogramaModal'
import { EmitirRecetaModal } from '../clinica/EmitirRecetaModal'
import { NuevaCitaDialog, type PrefijoCita } from '../agenda/NuevaCitaDialog'
import { NuevaNotaEvolucionModal } from '../clinica/NuevaNotaEvolucionModal'
import { ActualizarAntecedentesModal } from '../clinica/ActualizarAntecedentesModal'
import { SubirRadiografiaModal } from '../clinica/SubirRadiografiaModal'
import { VisorRadiografiaModal } from '../clinica/VisorRadiografiaModal'
import { FirmarConsentimientoModal } from '../clinica/FirmarConsentimientoModal'
import { VisorConsentimientoModal } from '../clinica/VisorConsentimientoModal'
import { ImprimirComprobanteModal } from '../caja/ImprimirComprobanteModal'
import { CerrarPlanModal } from './CerrarPlanModal'

// Cinco pestañas (antes ocho): "Tratamientos y pagos" reúne planes, cobros y saldo; "Estudios y consentimientos"
// reúne radiografías, fotos y documentos firmados. Las citas se ven en la tarjeta de próxima atención del Resumen.

const PESTANAS_FICHA = [
  { id: 'resumen', etiqueta: 'Resumen' },
  { id: 'ficha', etiqueta: 'Ficha de ingreso' },
  { id: 'historia', etiqueta: 'Historia Clínica' },
  { id: 'odontograma', etiqueta: 'Odontograma' },
  { id: 'tratamientos', etiqueta: 'Tratamientos y pagos' },
  { id: 'estudios', etiqueta: 'Estudios y consentimientos' },
] as const

type PestañaFicha = (typeof PESTANAS_FICHA)[number]['id']

/** Nombres antiguos de pestaña (?tab=…) y a cuál pasaron. */
const ALIAS_PESTANA: Record<string, PestañaFicha> = {
  clinica: 'historia',
  radiografias: 'estudios',
  consentimientos: 'estudios',
  finanzas: 'tratamientos',
  pagos: 'tratamientos',
  citas: 'resumen',
}


/** ?tab=… de la URL → pestaña. Acepta también los nombres antiguos; un valor desconocido se ignora. */
function pestañaDesdeUrl(tab: string | undefined): PestañaFicha | null {
  if (tab && tab in ALIAS_PESTANA) return ALIAS_PESTANA[tab]
  return PESTANAS_FICHA.find((p) => p.id === tab)?.id ?? null
}

const FUENTES_ETIQUETA: Record<string, string> = {
  FACEBOOK: 'Facebook Ads',
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
  GOOGLE: 'Google Search',
  REFERIDO_PACIENTE: 'Recomendación de paciente',
  REFERIDO_DOCTOR: 'Derivación Médica Externa',
  LOCAL: 'Pasaba por el local',
  OTRO: 'Otro origen',
}

export function FichaPacientePage() {
  const { id } = useParams({ strict: false }) as { id?: string }
  const { tab, desde } = useSearch({ strict: false }) as { tab?: string; desde?: 'agenda' | 'bandeja' }
  const sesion = useSesion()
  const [pestañaActiva, setPestañaActiva] = useState<PestañaFicha>(() => {
    const deUrl = pestañaDesdeUrl(tab)
    if (sesion?.negocioActivo === 'VILLAMAX' && deUrl === 'odontograma') return 'resumen'
    return deUrl ?? 'resumen'
  })
  // La pestaña de la URL manda cuando cambia (p. ej. al navegar desde otra pantalla con ?tab=).
  useAlCambiar([tab, sesion?.negocioActivo], () => {
    let deUrl = pestañaDesdeUrl(tab)
    if (sesion?.negocioActivo === 'VILLAMAX' && deUrl === 'odontograma') {
      deUrl = 'resumen'
    }
    if (deUrl) {
      setPestañaActiva(deUrl)
    } else if (sesion?.negocioActivo === 'VILLAMAX' && pestañaActiva === 'odontograma') {
      setPestañaActiva('resumen')
    }
  })
  const [modalPagoAbierto, setModalPagoAbierto] = useState(false)
  const [modalTratamientoAbierto, setModalTratamientoAbierto] = useState(false)
  const [tratamientoParaCobro, setTratamientoParaCobro] = useState<TratamientoDTO | undefined>(undefined)
  const [itemsTratamientoDesdeOdonto, setItemsTratamientoDesdeOdonto] = useState<
    Array<{ nombreProcedimiento: string; piezaFdi?: number; precioBase?: string }> | undefined
  >(undefined)

  /* Modales Clínicos Fase 5 */
  const [modalNotaAbierto, setModalNotaAbierto] = useState(false)
  const [recetaAbierta, setRecetaAbierta] = useState(false)
  const [notaReferenciaIdAdenda, setNotaReferenciaIdAdenda] = useState<string | undefined>(undefined)
  const [modalAntecedentesAbierto, setModalAntecedentesAbierto] = useState(false)
  const [modalPiezaAbierto, setModalPiezaAbierto] = useState(false)
  const [piezaSeleccionada, setPiezaSeleccionada] = useState<number | undefined>(undefined)
  const [denticion, setDenticion] = useState<'PERMANENTE' | 'DECIDUA'>('PERMANENTE')
  const [modalSubirRadioAbierto, setModalSubirRadioAbierto] = useState(false)
  const [radiografiaSeleccionada, setRadiografiaSeleccionada] = useState<RadiografiaDTO | null>(null)
  const [modalConsentimientoAbierto, setModalConsentimientoAbierto] = useState(false)
  const [consentimientoSeleccionado, setConsentimientoSeleccionado] = useState<ConsentimientoDTO | null>(null)
  const [comprobanteTratamiento, setComprobanteTratamiento] = useState<TratamientoDTO | null>(null)
  const datosSedeComprobante = useDatosSede(comprobanteTratamiento?.sedeId)
  const [nuevaCita, setNuevaCita] = useState<PrefijoCita | null>(null)

    const qc = useQueryClient()

  /* Consultas Backend */
  const { data: paciente, isLoading, isError, error, refetch } = usePacienteQuery(id)
  const [corrigiendo, setCorrigiendo] = useState(false)
  const solicitudesCorreccion = useSolicitudesDePaciente(id)
  const correccionesPendientes = (solicitudesCorreccion.data ?? []).filter((s) => s.estado === 'PENDIENTE')
  const { data: tratamientos = [], refetch: recargarTratamientos, isPending: cargandoTratamientos, isError: errorTratamientos } =
    useTratamientosPaciente(id)
  const { data: pagos = [], refetch: recargarPagos } = usePagosPaciente(id)
  const descuentosRecomendacion = useDescuentosRecomendacion(id)
  const { data: notasEvolucion = [], refetch: recargarNotas, isPending: cargandoNotas, isError: errorNotas } = useNotasEvolucion(id)
  const { data: registrosOdontograma = [], refetch: recargarOdontograma } = useOdontogramaActual(id)
  const { data: radiografias = [], refetch: recargarRadiografias, isPending: cargandoRadiografias, isError: errorRadiografias } =
    useRadiografiasPaciente(id)
  const { data: consentimientos = [], refetch: recargarConsentimientos } = useConsentimientosPaciente(id)
  const { data: accesosHistoria = [] } = useAccesosHistoria(
    sesion?.usuario?.rol === 'ADMIN_GENERAL' ? id : undefined
  )

  /* Registrar apertura de historia clínica según Ley 29733 */
  useEffect(() => {
    if (id) {
      registrarAperturaHistoria(id).catch(() => {
        // No interrumpir la UX si falla el registro de auditoría
      })
    }
  }, [id])

  const aceptarTratamientoMutation = useAceptarTratamientoMutation()
  const actualizarTratamientoMutation = useActualizarTratamientoMutation()
  const [errorTratamiento, setErrorTratamiento] = useState<string | null>(null)

  /** +1 sesión; al llegar al total el plan pasa solo a COMPLETADO. */
  async function registrarSesion(t: TratamientoDTO) {
    const nueva = (t.sesionesRealizadas ?? 0) + 1
    const total = t.totalSesiones ?? 1
    setErrorTratamiento(null)
    try {
      await actualizarTratamientoMutation.mutateAsync({
        id: t.id!,
        dto: { sesionesRealizadas: nueva, estado: nueva >= total ? 'COMPLETADO' : 'EN_PROCESO' },
      })
      toast(`Sesión registrada: ${t.nombre} (${nueva}/${total})`)
    } catch (error) {
      setErrorTratamiento(mensajeError(error))
    }
  }

  const [planParaCerrar, setPlanParaCerrar] = useState<TratamientoDTO | null>(null)

  async function handleCerrarPlan(estado: 'TERMINADO' | 'INTERRUMPIDO', motivo?: string) {
    if (!planParaCerrar?.id) return
    setErrorTratamiento(null)
    try {
      await actualizarTratamientoMutation.mutateAsync({
        id: planParaCerrar.id,
        dto: { estado, motivoCierre: motivo },
      })
      toast(`Plan cerrado: ${planParaCerrar.nombre} (${estado === 'TERMINADO' ? 'Terminado' : 'Interrumpido'})`)
    } catch (error) {
      setErrorTratamiento(mensajeError(error))
      throw error
    }
  }

  async function finalizarTratamiento(t: TratamientoDTO) {
    setErrorTratamiento(null)
    try {
      await actualizarTratamientoMutation.mutateAsync({ id: t.id!, dto: { estado: 'COMPLETADO' } })
      toast(`Tratamiento finalizado: ${t.nombre}`)
    } catch (error) {
      setErrorTratamiento(mensajeError(error))
    }
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-44 w-full rounded-card" />
        <Skeleton className="h-96 w-full rounded-card" />
      </div>
    )
  }

  if (isError || !paciente) {
    if (esSinPermiso(error)) {
      return (
        <div className="mx-auto max-w-3xl rounded-card bg-surface-container-lowest shadow-card p-6">
          <ErrorState
            icono={<LockKeyhole />}
            titulo="Sin permiso"
            descripcion={mensajeError(error)}
            accion={
              <Link to="/app/pacientes" className={buttonVariants({ variant: 'primario' })}>
                Volver al Directorio
              </Link>
            }
          />
        </div>
      )
    }

    return (
      <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-8 text-center space-y-4">
        <p className="text-error font-medium">No se pudo cargar la ficha del paciente.</p>
        <div className="flex justify-center gap-3">
          <Button variant="secundario" onClick={() => refetch()}>
            Reintentar
          </Button>
          <Link to="/app/pacientes">
            <Button variant="primario">Volver al Directorio</Button>
          </Link>
        </div>
      </div>
    )
  }

  const descuentoPendiente = descuentosRecomendacion.data?.find((d) => d.estado === 'PENDIENTE')

  const esVillamax =
    paciente.sedeId === 'sede-villamax-chilca' || paciente.fuenteCaptacion === 'REFERIDO_DOCTOR'

  const mensajeWhatsApp = encodeURIComponent(
    `Hola ${paciente.nombres}, le saludamos de Villadent / Villamax respecto a su atención odontológica.`
  )

  const registroPiezaSeleccionada = registrosOdontograma.find((r) => r.piezaFdi === piezaSeleccionada)

  return (
    <div className="space-y-6">
      {/* Breadcrumbs y Navegación */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Migas de pan" className="flex items-center gap-2 text-xs text-on-surface-variant">
          <Link to="/app/pacientes" className="hover:text-primary transition-colors">
            Pacientes
          </Link>
          <span>/</span>
          <span className="text-on-surface font-medium">Ficha Clínica</span>
          <span>/</span>
          <span className="text-primary font-semibold">{paciente.nombreCompleto}</span>
        </nav>
        {desde && (
          <Link
            to={desde === 'bandeja' ? '/app/bandeja' : '/app/agenda'}
            className={buttonVariants({ variant: 'secundario', size: 'sm' })}
          >
            {desde === 'bandeja' ? '← Volver a la Bandeja del día' : '← Volver a la Agenda'}
          </Link>
        )}
      </div>

      {/* Hero Header Stitch B9: Ficha Integral del Paciente */}
      <header className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-card space-y-4">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div className="flex items-center gap-4">
            <FotoPaciente
              pacienteId={paciente.id!}
              nombres={paciente.nombres}
              apellidos={paciente.apellidos}
              fotoActualizadaEn={paciente.fotoActualizadaEn}
              tamano="lg"
              editable
            />

            <div className="min-w-0 space-y-1">
              <div className="flex items-center gap-2.5 flex-wrap">
                <h1 className="font-display text-2xl font-bold text-on-surface sm:text-3xl">
                  {paciente.nombreCompleto}
                </h1>
                <Badge tono="neutro">{(paciente.edad ?? 0) > 0 ? `${paciente.edad} años` : 'Edad no reg.'}</Badge>
              </div>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-on-surface-variant font-mono">
                <span>DNI: <strong className="text-on-surface">{paciente.dni}</strong></span>
                <span>•</span>
                <span>HC: <strong className="text-on-surface">{paciente.numeroHistoriaClinica ?? `HC-${paciente.dni}`}</strong></span>
                {paciente.fechaNacimiento && (
                  <>
                    <span>•</span>
                    <span>Nac: {formatFecha(paciente.fechaNacimiento)}</span>
                  </>
                )}
                {paciente.telefono && (
                  <>
                    <span>•</span>
                    <span>Tel: +51 {paciente.telefono}</span>
                  </>
                )}
                {paciente.email && (
                  <>
                    <span>•</span>
                    <span>{paciente.email}</span>
                  </>
                )}
              </div>

              <div className="text-xs text-on-surface-variant flex items-center gap-1.5 pt-0.5">
                <span>Origen de Captación:</span>
                <span className="font-medium text-primary">
                  {FUENTES_ETIQUETA[paciente.fuenteCaptacion ?? ''] ?? paciente.fuenteCaptacion ?? 'Directo'}
                  {paciente.referidoPorPacienteNombre ? ` (${paciente.referidoPorPacienteNombre})` : ''}
                  {paciente.doctorReferidor ? ` (${paciente.doctorReferidor})` : ''}
                </span>
              </div>
            </div>
          </div>

          {/* Botones de Acción Inmediata: Alineados sin espacios sueltos */}
          <div className="flex flex-col items-start xl:items-end gap-1.5 xl:shrink-0">
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="accion" className="shadow-sm" onClick={() => setNuevaCita({ paciente })}>
                + Nueva Cita
              </Button>

              {paciente.telefono && (
                <a href={`tel:${paciente.telefono.replace(/\D/g, '')}`}>
                  <Button variant="secundario" className="border border-outline-variant/40 gap-1.5">
                    <Phone aria-hidden className="size-3.5" />
                    Llamar
                  </Button>
                </a>
              )}

              {paciente.telefono && (
                <a
                  href={`https://wa.me/51${paciente.telefono.replace(/\D/g, '')}?text=${mensajeWhatsApp}`}
                  target="_blank"
                  rel="noreferrer"
                  aria-label="Escribir WhatsApp"
                >
                  <Button variant="secundario" className="bg-emerald-600 text-white hover:bg-emerald-700 border-none gap-1.5 shadow-xs">
                    <svg className="size-3.5 fill-current" viewBox="0 0 24 24">
                      <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946.003-6.556 5.338-11.891 11.893-11.891 3.181.001 6.167 1.24 8.413 3.488 2.245 2.248 3.481 5.236 3.48 8.414-.003 6.557-5.338 11.892-11.893 11.892-1.99-.001-3.951-.5-5.688-1.448l-6.305 1.654zm6.597-3.807c1.676.995 3.276 1.591 5.392 1.592 5.448 0 9.886-4.434 9.889-9.885.002-5.462-4.415-9.89-9.881-9.892-5.452 0-9.887 4.434-9.889 9.884-.001 2.225.651 3.891 1.746 5.634l-.999 3.648 3.742-.981z" />
                    </svg>
                    WhatsApp
                  </Button>
                </a>
              )}

              <Button
                variant="secundario"
                onClick={() => {
                  const tratConDeuda = tratamientos.find((t) => (t.saldoPendiente ?? 0) > 0)
                  setTratamientoParaCobro(tratConDeuda)
                  setModalPagoAbierto(true)
                }}
                className="font-bold shadow-accion"
              >
                Cobrar en Caja
              </Button>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-outline-variant/30">
          <Button type="button" variant="secundario" onClick={() => setCorrigiendo(true)}>
            Solicitar corrección de nombre, DNI o fecha de nacimiento
          </Button>
          {correccionesPendientes.map((s) => (
            <span key={s.id} className="text-xs text-on-surface-variant">
              Pendiente de autorización del dueño: {cambiosDe(s).join('; ')}
            </span>
          ))}
        </div>
        <SolicitarCorreccionModal
          abierto={corrigiendo}
          alCerrar={() => setCorrigiendo(false)}
          pacienteId={paciente.id!}
          actual={{ nombres: paciente.nombres, apellidos: paciente.apellidos, dni: paciente.dni, fechaNacimiento: paciente.fechaNacimiento }}
        />

        {sesion?.usuario.rol === 'ADMIN_GENERAL' && (
          <div className="pt-2 border-t border-outline-variant/30">
            <AccionesAdminPaciente pacienteId={paciente.id!} nombre={paciente.nombreCompleto ?? 'el paciente'} />
          </div>
        )}

        {/* Banner de Alerta Médica Crítica (Alergias / Gestación / Antecedentes Sistémicos) */}
        {(paciente.alergias?.length || paciente.antecedentesMedicos || paciente.gestante) ? (
          <div
            role="alert"
            className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-lg border-2 border-red-500/50 bg-red-50 dark:bg-red-950/40 p-3.5 text-red-900 dark:text-red-200 shadow-sm"
          >
            <div className="flex items-start sm:items-center gap-2.5">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-red-600 text-white font-bold text-xs" aria-hidden>
                ⚠️
              </span>
              <div className="space-y-0.5 text-xs">
                <p className="font-bold uppercase tracking-wide text-red-700 dark:text-red-400 text-xs">
                  Alerta Médica de Seguridad Clínica
                </p>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-semibold">
                  {paciente.alergias && paciente.alergias.length > 0 && (
                    <span>
                      <strong className="text-red-700 dark:text-red-300">Alergias:</strong> {paciente.alergias.join(', ')}
                    </span>
                  )}
                  {paciente.antecedentesMedicos && (
                    <span>
                      <strong>Antecedentes:</strong> {paciente.antecedentesMedicos}
                    </span>
                  )}
                  {paciente.gestante && (
                    <span>
                      <strong>Gestación:</strong> {paciente.semanasGestacion ? `${paciente.semanasGestacion} semanas` : 'Sí'}
                    </span>
                  )}
                </div>
              </div>
            </div>
            <Button
              size="sm"
              variant="secundario"
              onClick={() => setModalAntecedentesAbierto(true)}
              className="text-xs shrink-0 bg-white dark:bg-surface-container-high border-red-300 text-red-800 dark:text-red-300 hover:bg-red-100"
            >
              Editar Alertas Médicas
            </Button>
          </div>
        ) : null}

        {/* Alerta Preventiva de Saldo Deudor antes de Atención */}
        {(paciente.saldoPendiente ?? 0) > 0 && (
          <div
            role="alert"
            className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-panel border border-brand-gold/40 bg-brand-gold/10 p-4 text-on-surface"
          >
            <div className="flex items-start sm:items-center gap-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-gold/20 text-primary" aria-hidden>
                <Wallet className="size-4.5" />
              </span>
              <div className="space-y-0.5">
                <p className="text-sm font-bold text-primary">Saldo pendiente: {formatSoles(paciente.saldoPendiente)}</p>
                <p className="text-sm text-on-surface-variant">
                  Tiene cuotas por cobrar en sus tratamientos. Puede abonar en caja cuando venga a su cita.
                </p>
              </div>
            </div>
            <Button
              size="sm"
              variant="accion"
              onClick={() => {
                const tratConDeuda = tratamientos.find((t) => (t.saldoPendiente ?? 0) > 0)
                setTratamientoParaCobro(tratConDeuda)
                setModalPagoAbierto(true)
              }}
              className="text-xs shrink-0 font-bold"
            >
              Abonar Cuota en Caja
            </Button>
          </div>
        )}

        {/* Tira de Badges Clínicos y de Alertas Stitch B9 */}
        <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-outline-variant/30">
          {esVillamax && (
            <Badge tono="villamax">
              Paciente Villamax 3D
            </Badge>
          )}

          {(paciente.saldoPendiente ?? 0) > 0 && (
            <Badge tono="peligro">
              💳 Deuda Pendiente: {formatSoles(paciente.saldoPendiente)}
            </Badge>
          )}
          {esSaldoAFavor(paciente.saldoPendiente) && (
            <Badge tono="neutro" className="text-emerald-700">
              {formatoSaldo(paciente.saldoPendiente)}
            </Badge>
          )}

          {paciente.alergias && paciente.alergias.length > 0 && (
            <Badge tono="peligro">
              ⚠️ Alergia: {paciente.alergias.join(', ')}
            </Badge>
          )}

          {paciente.avisoInasistencias && (
            <Badge tono="peligro">
              Aviso: {paciente.totalInasistencias} inasistencias previas
            </Badge>
          )}

          {paciente.esCumpleaneroMes && (
            <Badge tono="accion">
              🎁 Promo Cumpleaños vigente (20% OFF en Profilaxis)
            </Badge>
          )}

          {paciente.consentimientoPromociones && (
            <Badge tono="neutro">
              ✓ Consentimiento Ley 29733 Aceptado
            </Badge>
          )}

          {paciente.gestante && (
            <Badge tono="peligro">
              Gestante {paciente.semanasGestacion ? `(${paciente.semanasGestacion} semanas)` : ''}
            </Badge>
          )}

          {descuentoPendiente && (
            <Badge tono="accion">
              Descuento por recomendación pendiente · recomendó a {descuentoPendiente.referidoNombre ?? 'un paciente nuevo'}
            </Badge>
          )}
          <Button
            size="sm"
            variant="fantasma"
            onClick={() => setModalAntecedentesAbierto(true)}
            className="text-xs h-6 px-2 text-on-surface-variant hover:text-primary"
            title="Actualizar antecedentes médicos y alergias"
          >
            + Actualizar Antecedentes
          </Button>
        </div>
      </header>

      {/* Pestañas de la Ficha Integral Stitch B9 */}
      <Tabs
        value={pestañaActiva}
        onValueChange={(val: string) => setPestañaActiva(val as PestañaFicha)}
      >
        <TabsList className="overflow-x-auto justify-start">
          {PESTANAS_FICHA.filter((p) => (sesion?.negocioActivo === 'VILLAMAX' ? p.id !== 'odontograma' : true)).map((p) => (
            <TabsTrigger key={p.id} value={p.id}>
              {p.etiqueta}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {/* Contenido de la pestaña activa */}
      {pestañaActiva === 'ficha' && (
        <FichaIngresoPanel pacienteId={paciente.id!} edad={paciente.edad} alGuardadoSeguir={esVillamax ? undefined : () => setPestañaActiva('odontograma')} />
      )}

      {pestañaActiva === 'resumen' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Columna Principal (2 columnas de ancho en desktop) */}
          <div className="space-y-6 lg:col-span-2">
            <TarjetaProximaCita pacienteId={paciente.id!} puedeAgendar={puede(sesion?.usuario.rol, 'agendarCita')} />

            <TarjetaTratamientoActivo
              tratamientos={tratamientos}
              estado={estadoDe(cargandoTratamientos, errorTratamientos)}
              onVerPlanes={() => setPestañaActiva('tratamientos')}
              onNuevoPlan={() => setModalTratamientoAbierto(true)}
            />



            {/* Última nota de evolución: datos reales o un vacío honesto (nunca texto de ejemplo) */}
            <MarcoResumen
              titulo="Última nota de evolución odontológica"
              estado={estadoDe(cargandoNotas, errorNotas)}
              derecha={
                paciente.ultimaCitaAtendida ? (
                  <span className="text-xs text-on-surface-variant">Última atención: {formatFecha(paciente.ultimaCitaAtendida)}</span>
                ) : null
              }
            >
              {notasEvolucion.length > 0 ? (
                <div className="rounded bg-surface-container-low p-3.5 border border-outline-variant/40 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-on-surface">{notasEvolucion[0].autorNombre}</span>
                    <span className="text-on-surface-variant">{formatFechaHora(notasEvolucion[0].creadoEn)}</span>
                  </div>
                  {notasEvolucion[0].tipoNota === 'SOAP' ? (
                    <div className="text-xs space-y-1">
                      {notasEvolucion[0].subjetivo && <p><strong className="text-primary">S:</strong> {notasEvolucion[0].subjetivo}</p>}
                      {notasEvolucion[0].objetivo && <p><strong className="text-secondary">O:</strong> {notasEvolucion[0].objetivo}</p>}
                      {notasEvolucion[0].analisis && <p><strong className="text-amber-600">A:</strong> {notasEvolucion[0].analisis}</p>}
                      {notasEvolucion[0].plan && <p><strong className="text-emerald-700">P:</strong> {notasEvolucion[0].plan}</p>}
                    </div>
                  ) : (
                    <p className="text-xs italic text-on-surface">{notasEvolucion[0].contenido}</p>
                  )}
                </div>
              ) : (
                <div className="space-y-3">
                  <p className="rounded bg-surface-container-low p-3.5 text-sm text-on-surface-variant">
                    Aún no hay notas de evolución registradas para este paciente.
                  </p>
                  <Button variant="secundario" size="sm" onClick={() => setModalNotaAbierto(true)}>
                    + Nueva nota de evolución
                  </Button>
                </div>
              )}
            </MarcoResumen>

            <TarjetaEstudios
              radiografias={radiografias}
              estado={estadoDe(cargandoRadiografias, errorRadiografias)}
              onVerGaleria={() => setPestañaActiva('estudios')}
            />
          </div>

          {/* Columna Lateral (1 columna de ancho en desktop) */}
          <div className="space-y-6">
            {/* Estado Financiero Stitch B9 */}
            <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-4">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
                <h3 className="font-display text-base font-bold text-on-surface">Estado Financiero</h3>
                <span className="text-xs font-mono text-on-surface-variant">Soles (S/)</span>
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-on-surface-variant">Saldo:</span>
                  <span className={`font-bold font-mono ${esSaldoAFavor(paciente.saldoPendiente) ? 'text-emerald-700' : (paciente.saldoPendiente ?? 0) > 0 ? 'text-error' : 'text-emerald-700'}`}>
                    {formatoSaldo(paciente.saldoPendiente)}
                  </span>
                </div>

                <div className="flex items-center justify-between text-sm">
                  <span className="text-on-surface-variant">Condición:</span>
                  <span className="font-semibold text-on-surface">
                    {(paciente.saldoPendiente ?? 0) > 0
                      ? 'Cuotas por cobrar'
                      : esSaldoAFavor(paciente.saldoPendiente) ? 'Saldo a favor' : 'Al día'}
                  </span>
                </div>

                <Button
                  variant="accion"
                  className="w-full mt-2"
                  onClick={() => {
                    const tratConDeuda = tratamientos.find((t) => (t.saldoPendiente ?? 0) > 0)
                    setTratamientoParaCobro(tratConDeuda)
                    setModalPagoAbierto(true)
                  }}
                >
                  Registrar cobro
                </Button>
              </div>
            </section>

            {/* Ficha Médica & Triage Stitch B9 */}
            <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="font-display text-base font-bold text-on-surface">Ficha Médica & Triage</h3>
                <Button
                  variant="secundario"
                  className="text-xs py-1 px-2.5"
                  onClick={() => setModalAntecedentesAbierto(true)}
                >
                  Editar
                </Button>
              </div>

              <div className="space-y-2 text-xs">
                <div className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                  <p className="font-semibold text-on-surface">Alergias Conocidas</p>
                  <p className="text-on-surface-variant mt-0.5">
                    {paciente.alergias && paciente.alergias.length > 0
                      ? paciente.alergias.join(', ')
                      : 'Ninguna alergia registrada'}
                  </p>
                </div>

                <div className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                  <p className="font-semibold text-on-surface">Antecedentes Clínicos</p>
                  <p className="text-on-surface-variant mt-0.5">
                    {paciente.antecedentesMedicos || 'Sin antecedentes reportados'}
                  </p>
                </div>
              </div>
            </section>

            {/* Historial de Accesos — solo ADMIN_GENERAL */}
            {sesion?.usuario?.rol === 'ADMIN_GENERAL' && accesosHistoria.length > 0 && (
              <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-3">
                <h3 className="font-display text-base font-bold text-on-surface">
                  Auditoría de Accesos (Ley 29733)
                </h3>
                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                  {accesosHistoria.slice(0, 10).map((a) => (
                    <div key={a.id} className="rounded border border-outline-variant/40 bg-surface-container-low p-2 text-xs">
                      <div className="flex justify-between">
                        <span className="font-semibold text-on-surface">{a.usuarioNombre}</span>
                        <span className="text-on-surface-variant font-mono">{formatFechaHora(a.accedidoEn)}</span>
                      </div>
                      <p className="text-on-surface-variant">{a.usuarioRol}{a.ip ? ` · ${a.ip}` : ''}</p>
                    </div>
                  ))}
                </div>
              </section>
            )}

          {/* Consentimientos y Documentos Stitch B9 */}
            <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="font-display text-base font-bold text-on-surface">Consentimientos</h3>
                <Button
                  variant="secundario"
                  className="text-xs py-1 px-2.5"
                  onClick={() => setModalConsentimientoAbierto(true)}
                >
                  + Firmar
                </Button>
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex items-center justify-between rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                  <div>
                    <p className="font-semibold text-on-surface">Consentimientos Firmados</p>
                    <p className="text-xs text-on-surface-variant">Documentos legales archivados</p>
                  </div>
                  <Badge tono={consentimientos.length > 0 ? 'primario' : 'neutro'}>
                    {consentimientos.length} registrados
                  </Badge>
                </div>

                <div className="flex items-center justify-between rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                  <div>
                    <p className="font-semibold text-on-surface">Ley 29733 (Datos Personales)</p>
                    <p className="text-xs text-on-surface-variant">Promociones y Mensajería</p>
                  </div>
                  <Badge tono={paciente.consentimientoPromociones ? 'primario' : 'neutro'}>
                    {paciente.consentimientoPromociones ? 'Autorizado' : 'Sin autorizar'}
                  </Badge>
                </div>
              </div>
            </section>
          </div>
        </div>
      )}

      {/* ─────────── Pestaña: Historia Clínica (Fase 5) ─────────── */}
      {pestañaActiva === 'historia' && (
        <div className="space-y-6">
          {/* Bloque de Antecedentes y Alergias */}
          <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-card space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-3 border-b border-outline-variant/30">
              <div>
                <h2 className="font-display text-lg font-bold text-on-surface flex items-center gap-2">
                  <span>🛡️</span>
                  <span>Antecedentes Médicos, Alergias y Triage Clínico</span>
                </h2>
                <p className="text-xs text-on-surface-variant">
                  Información crítica para la seguridad del paciente antes de intervenciones quirúrgicas o prescripción
                </p>
              </div>
              <Button
                variant="secundario"
                onClick={() => setModalAntecedentesAbierto(true)}
                className="text-xs py-1.5 px-3"
              >
                Actualizar Antecedentes
              </Button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
              <div className="rounded-lg border border-outline-variant/40 bg-surface-container-low p-3.5 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-on-surface">Alergias Registradas</span>
                  {paciente.alergias && paciente.alergias.length > 0 ? (
                    <Badge tono="peligro">Crítico</Badge>
                  ) : (
                    <Badge tono="primario">Sin Alergias</Badge>
                  )}
                </div>
                <p className="text-on-surface leading-relaxed">
                  {paciente.alergias && paciente.alergias.length > 0
                    ? paciente.alergias.join(', ')
                    : 'No refiere alergias a medicamentos ni materiales.'}
                </p>
              </div>

              <div className="rounded-lg border border-outline-variant/40 bg-surface-container-low p-3.5 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-on-surface">Antecedentes Sistémicos</span>
                  <Badge tono="neutro">Historia Médica</Badge>
                </div>
                <p className="text-on-surface leading-relaxed">
                  {paciente.antecedentesMedicos || 'Sin antecedentes patológicos declarados.'}
                </p>
              </div>

              <div className="rounded-lg border border-outline-variant/40 bg-surface-container-low p-3.5 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-on-surface">Condición Especial</span>
                  {paciente.gestante ? (
                    <Badge tono="peligro">Gestante</Badge>
                  ) : (
                    <Badge tono="neutro">Estándar</Badge>
                  )}
                </div>
                <p className="text-on-surface leading-relaxed">
                  {paciente.gestante
                    ? `Paciente gestante (${paciente.semanasGestacion ?? 0} semanas). Precaución con Rx y anestésicos.`
                    : 'Paciente no gestante.'}
                </p>
              </div>
            </div>
          </section>

          {puede(sesion?.usuario.rol, 'emitirReceta') && paciente.id && (
            <FichasPorTratamiento
              pacienteId={paciente.id}
              pacienteNombre={paciente.nombreCompleto}
              negocio={sesion?.negocioActivo === 'VILLAMAX' ? 'VILLAMAX' : 'VILLADENT'}
              planesEnCurso={tratamientos.filter((t) => t.estado === 'EN_PROCESO').map((t) => ({ id: t.id, nombre: t.nombre }))}
            />
          )}

          {/* Bloque de Notas de Evolución SOAP */}
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Notas de Evolución Odontológica
              </h2>
              <p className="text-xs text-on-surface-variant">
                Registro cronológico con metodología SOAP (Subjetivo, Objetivo, Análisis, Plan) y firma del facultativo
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {puede(sesion?.usuario.rol, 'emitirReceta') && (
                <Button variant="secundario" onClick={() => setRecetaAbierta(true)} className="text-xs py-1.5 px-3">
                  <FileText aria-hidden /> Nueva receta
                </Button>
              )}
              <Button
                variant="accion"
                onClick={() => setModalNotaAbierto(true)}
                className="text-xs py-1.5 px-3"
              >
                + Nueva Nota de Evolución
              </Button>
            </div>
          </div>

          {notasEvolucion.length === 0 ? (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-surface-container text-on-surface-variant">
                📝
              </div>
              <div className="space-y-1">
                <h3 className="font-display text-base font-bold text-on-surface">
                  No hay notas de evolución registradas
                </h3>
                <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                  Registre la primera nota clínica en formato SOAP o formato libre para este paciente.
                </p>
              </div>
              <Button
                variant="accion"
                onClick={() => setModalNotaAbierto(true)}
              >
                Redactar Primera Nota SOAP
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              {notasEvolucion.map((nota) => (
                <article
                  key={nota.id}
                  className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-3"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-2.5 border-b border-outline-variant/30">
                    <div className="flex items-center gap-2.5">
                      <div className="size-8 rounded-full bg-acento text-on-acento flex items-center justify-center font-bold text-xs">
                        Dr
                      </div>
                      <div>
                        <h4 className="font-bold text-sm text-on-surface">{nota.autorNombre}</h4>
                        <p className="text-xs text-on-surface-variant">
                          Rol: {nota.autorRol} · {formatFechaHora(nota.creadoEn)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge tono={nota.tipoNota === 'SOAP' ? 'primario' : 'neutro'}>
                        {nota.tipoNota === 'SOAP' ? 'Formato SOAP' : 'Texto Libre'}
                      </Badge>
                      {nota.notaReferenciaId ? (
                        <Badge tono="neutro">Adenda</Badge>
                      ) : (
                        <Badge tono="primario">✓ Firmado Digitalmente</Badge>
                      )}
                      {!nota.notaReferenciaId && (
                        <Button
                          variant="secundario"
                          className="text-xs py-0.5 px-2"
                          onClick={() => {
                            setNotaReferenciaIdAdenda(nota.id)
                            setModalNotaAbierto(true)
                          }}
                        >
                          + Adenda
                        </Button>
                      )}
                    </div>
                  </div>

                  {nota.tipoNota === 'SOAP' ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs pt-1">
                      {nota.subjetivo && (
                        <div className="rounded border border-primary/20 bg-primary/5 p-3">
                          <span className="font-bold text-primary block text-xs uppercase tracking-wider mb-1">
                            S — Subjetivo (Motivo / Síntomas):
                          </span>
                          <p className="text-on-surface leading-relaxed">{nota.subjetivo}</p>
                        </div>
                      )}

                      {nota.objetivo && (
                        <div className="rounded border border-secondary/20 bg-secondary/5 p-3">
                          <span className="font-bold text-secondary block text-xs uppercase tracking-wider mb-1">
                            O — Objetivo (Examen Clínico):
                          </span>
                          <p className="text-on-surface leading-relaxed">{nota.objetivo}</p>
                        </div>
                      )}

                      {nota.analisis && (
                        <div className="rounded border border-amber-500/20 bg-amber-500/5 p-3">
                          <span className="font-bold text-amber-700 block text-xs uppercase tracking-wider mb-1">
                            A — Análisis / Diagnóstico Clínico:
                          </span>
                          <p className="text-on-surface leading-relaxed">{nota.analisis}</p>
                        </div>
                      )}

                      {nota.plan && (
                        <div className="rounded border border-emerald-500/20 bg-emerald-500/5 p-3">
                          <span className="font-bold text-emerald-700 block text-xs uppercase tracking-wider mb-1">
                            P — Plan de Tratamiento e Indicaciones:
                          </span>
                          <p className="text-on-surface leading-relaxed">{nota.plan}</p>
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="rounded bg-surface-container-low p-3.5 text-xs text-on-surface leading-relaxed whitespace-pre-wrap">
                      {nota.contenido}
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ─────────── Pestaña: Odontograma 3D / Anatómico FDI (Fase 5) ─────────── */}
      {pestañaActiva === 'odontograma' && sesion?.negocioActivo !== 'VILLAMAX' && (
        <div className="space-y-6">
          <EpisodiosOdontograma pacienteId={paciente.id!} pacienteNombre={paciente.nombreCompleto ?? ''} pacienteDni={paciente.dni} sedeNombre={sesion?.usuario.sedes.find((x) => x.id === (paciente.sedeId ?? sesion?.sedeActivaId))?.nombre ?? ''} puedeEditar={['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'].includes(sesion?.usuario.rol ?? '')} />
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Odontograma Clínico Interactivo FDI
              </h2>
              <p className="text-xs text-on-surface-variant">
                Mapeo anatómico de 5 caras por pieza (Vestibular, Oclusal, Palatina/Lingual, Mesial, Distal)
              </p>
            </div>

            <div className="flex items-center gap-2">
              <div className="inline-flex rounded-lg border border-outline-variant/40 p-0.5 bg-surface-container-low text-xs">
                <button
                  type="button"
                  onClick={() => setDenticion('PERMANENTE')}
                  className={`px-3 py-1 rounded font-medium transition-all ${
                    denticion === 'PERMANENTE'
                      ? 'bg-acento text-on-acento shadow-sm'
                      : 'text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  Adultos (Permanente)
                </button>
                <button
                  type="button"
                  onClick={() => setDenticion('DECIDUA')}
                  className={`px-3 py-1 rounded font-medium transition-all ${
                    denticion === 'DECIDUA'
                      ? 'bg-acento text-on-acento shadow-sm'
                      : 'text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  Niños (Decidua)
                </button>
              </div>

              <Button
                variant="secundario"
                onClick={async () => {
                  const token = obtenerToken()
                  const base = import.meta.env.VITE_API_URL ?? '/api/v1'
                  const resp = await fetch(`${base}/odontograma/pacientes/${id}/pdf`, {
                    headers: token ? { Authorization: `Bearer ${token}` } : {},
                  })
                  if (!resp.ok) return
                  const blob = await resp.blob()
                  const url = URL.createObjectURL(blob)
                  const a = document.createElement('a')
                  a.href = url
                  a.download = `odontograma-${id}.pdf`
                  a.click()
                  URL.revokeObjectURL(url)
                }}
                className="text-xs py-1.5 px-3"
              >
                Imprimir PDF FDI
              </Button>

              {registrosOdontograma.some((r) =>
                ['caries', 'endodoncia', 'corona', 'implante', 'restauracion'].includes(r.estado?.toLowerCase() ?? '')
              ) && (
                <Button
                  variant="accion"
                  onClick={() => {
                    const patologicas = registrosOdontograma.filter((r) =>
                      ['caries', 'endodoncia', 'corona', 'implante', 'restauracion'].includes(r.estado?.toLowerCase() ?? '')
                    )
                    const mapeados = patologicas.map((p) => {
                      const est = p.estado?.toLowerCase()
                      let nombreProc = `Procedimiento en Pieza ${p.piezaFdi}`
                      let precio = '100.00'
                      if (est === 'caries') {
                        nombreProc = `Curación con Resina (Pieza ${p.piezaFdi})`
                        precio = '120.00'
                      } else if (est === 'endodoncia') {
                        nombreProc = `Endodoncia / Conducto (Pieza ${p.piezaFdi})`
                        precio = '350.00'
                      } else if (est === 'corona') {
                        nombreProc = `Corona Protésica (Pieza ${p.piezaFdi})`
                        precio = '450.00'
                      } else if (est === 'implante') {
                        nombreProc = `Implante de Titanio (Pieza ${p.piezaFdi})`
                        precio = '1500.00'
                      }
                      return {
                        nombreProcedimiento: nombreProc,
                        piezaFdi: p.piezaFdi,
                        precioBase: precio,
                      }
                    })
                    setItemsTratamientoDesdeOdonto(mapeados)
                    setModalTratamientoAbierto(true)
                  }}
                  className="text-xs py-1.5 px-3"
                >
                  ✨ Presupuesto ({registrosOdontograma.filter((r) => ['caries', 'endodoncia', 'corona', 'implante', 'restauracion'].includes(r.estado?.toLowerCase() ?? '')).length} piezas)
                </Button>
              )}

              <Button
                variant="secundario"
                onClick={() => {
                  setPiezaSeleccionada(undefined)
                  setModalPiezaAbierto(true)
                }}
                className="text-xs py-1.5 px-3"
              >
                + Registrar Pieza
              </Button>
            </div>
          </div>

          {/* Componente Gráfico Odontograma 1-Clic */}
          <OdontogramaVisual
            pacienteId={id}
            denticion={denticion}
            registros={registrosOdontograma}
            puedeEditar={true}
            onCambio={() => recargarOdontograma()}
            alSeleccionarPieza={(fdi) => {
              setPiezaSeleccionada(fdi)
              setModalPiezaAbierto(true)
            }}
            onRegistrarNuevaPieza={() => {
              setPiezaSeleccionada(undefined)
              setModalPiezaAbierto(true)
            }}
          />

          {/* Tabla de Piezas Registradas con Hallazgos */}
          {registrosOdontograma.length > 0 && (
            <section className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card space-y-3">
              <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30">
                <h3 className="font-display text-sm font-bold text-on-surface">
                  Detalle de Piezas con Diagnóstico Clínico ({registrosOdontograma.length})
                </h3>
                <span className="text-xs text-on-surface-variant">Haga clic en Editar para modificar hallazgos</span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                    <tr>
                      <th className="p-2.5">Pieza FDI</th>
                      <th className="p-2.5">Diagnóstico / Condición</th>
                      <th className="p-2.5">Caras Afectadas</th>
                      <th className="p-2.5">Observaciones</th>
                      <th className="p-2.5">Fecha</th>
                      <th className="p-2.5 text-right">Acción</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline">
                    {registrosOdontograma.map((reg) => (
                      <tr key={reg.id} className="hover:bg-surface-container-low/50">
                        <td className="p-2.5 font-bold font-mono text-primary">
                          Pieza {reg.piezaFdi}
                        </td>
                        <td className="p-2.5 uppercase font-semibold text-on-surface">
                          {reg.estado}
                        </td>
                        <td className="p-2.5 font-mono text-on-surface-variant">
                          {reg.caras && reg.caras.length > 0 ? reg.caras.join(', ') : 'Toda la pieza'}
                        </td>
                        <td className="p-2.5 text-on-surface">
                          {reg.observacion || '-'}
                        </td>
                        <td className="p-2.5 text-on-surface-variant font-mono">
                          {formatFecha(reg.registradoEn)}
                        </td>
                        <td className="p-2.5 text-right">
                          <Button
                            variant="secundario"
                            className="text-xs py-1 px-2.5"
                            onClick={() => {
                              setPiezaSeleccionada(reg.piezaFdi)
                              setModalPiezaAbierto(true)
                            }}
                          >
                            Modificar
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>
      )}

      {/* ─────────── Pestaña: Radiografías & 3D (Fase 5) ─────────── */}

      {pestañaActiva === 'estudios' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Estudios Radiológicos & Imagenología Maxilofacial 3D
              </h2>
              <p className="text-xs text-on-surface-variant">
                Panorámicas, periapicales, cefalometrías y tomografías Cone Beam sincronizadas con Villamax
              </p>
            </div>
            <Button
              variant="accion"
              onClick={() => setModalSubirRadioAbierto(true)}
              className="text-xs py-1.5 px-3"
            >
              + Subir Estudio Radiográfico
            </Button>
          </div>

          {radiografias.length === 0 ? (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-surface-container text-on-surface-variant">
                🦷
              </div>
              <div className="space-y-1">
                <h3 className="font-display text-base font-bold text-on-surface">
                  No hay estudios radiográficos cargados
                </h3>
                <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                  Suba estudios en formato imagen, DICOM o PDF para integrarlos al expediente clínico del paciente.
                </p>
              </div>
              <Button
                variant="accion"
                onClick={() => setModalSubirRadioAbierto(true)}
              >
                Subir Primer Estudio
              </Button>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {radiografias.map((rad) => {
                const esTomografia = rad.tipoEstudio === 'TOMOGRAFIA'
                return (
                  <div
                    key={rad.id}
                    className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-4 shadow-card flex flex-col justify-between space-y-3"
                  >
                    <div className="space-y-2">
                      <div className="relative h-40 w-full overflow-hidden rounded-lg bg-black/90 flex items-center justify-center border border-outline-variant/40">
                        {esTomografia ? (
                          <div className="text-center p-4">
                            <span className="text-3xl">🔬</span>
                            <p className="text-xs font-bold text-white mt-1">Villamax CBCT 3D</p>
                            <span className="text-xs text-neutral-400">Cone Beam Maxilofacial</span>
                          </div>
                        ) : rad.archivoUrl ? (
                          <img
                            src={resolverUrlRadiografia(rad)}
                            alt={rad.nombreArchivo}
                            className="h-full w-full object-cover"
                            onError={(e) => {
                              e.currentTarget.style.display = 'none'
                              if (e.currentTarget.parentElement) {
                                e.currentTarget.parentElement.innerHTML = `
                                  <div class="p-4 text-center text-neutral-300">
                                    <div class="text-3xl mb-1">🦷</div>
                                    <p class="font-bold text-xs">Estudio 2D</p>
                                    <p class="text-xs text-neutral-400">${rad.nombreArchivo}</p>
                                  </div>
                                `
                              }
                            }}
                          />
                        ) : (
                          <div className="text-center text-neutral-400 text-xs">
                            <span className="text-3xl block mb-1">📋</span>
                            <span>Documento Radiológico</span>
                          </div>
                        )}
                      </div>

                      <div className="flex items-center justify-between">
                        <Badge tono={esTomografia ? 'villamax' : 'primario'}>
                          {rad.tipoEstudio}
                        </Badge>
                        <span className="text-xs font-mono text-on-surface-variant">
                          {formatFecha(rad.fechaToma)}
                        </span>
                      </div>

                      <h4 className="font-bold text-sm text-on-surface truncate" title={rad.nombreArchivo}>
                        {rad.nombreArchivo}
                      </h4>

                      {rad.piezaFdi && (
                        <p className="text-xs text-primary font-mono font-semibold">
                          Pieza FDI: {rad.piezaFdi}
                        </p>
                      )}

                      {rad.diagnosticoRadiografico && (
                        <p className="text-xs text-on-surface-variant line-clamp-2 italic">
                          "{rad.diagnosticoRadiografico}"
                        </p>
                      )}
                    </div>

                    <Button
                      variant={esTomografia ? 'villamax' : 'secundario'}
                      className="w-full text-xs"
                      onClick={() => setRadiografiaSeleccionada(rad)}
                    >
                      {esTomografia ? 'Ver Estudio CBCT 3D' : 'Ver Radiografía HD'}
                    </Button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* ─────────── Pestaña: Tratamientos ─────────── */}
      {pestañaActiva === 'tratamientos' && (
        <div className="space-y-6">
          <PresupuestosPaciente pacienteId={paciente.id!} pacienteNombre={paciente.nombreCompleto ?? ''} pacienteDni={paciente.dni} pacienteTelefono={paciente.telefono} sedeId={paciente.sedeId ?? sesion?.sedeActivaId ?? ''} sedeNombre={sesion?.usuario.sedes.find((x) => x.id === (paciente.sedeId ?? sesion?.sedeActivaId))?.nombre ?? ''} />
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Planes de Tratamiento & Presupuestos
              </h2>
              <p className="text-xs text-on-surface-variant">
                Procedimientos clínicos planificados, seguimiento de sesiones y presupuestos aprobados
              </p>
            </div>
            <Button
              variant="accion"
              onClick={() => setModalTratamientoAbierto(true)}
              className="text-xs py-1.5 px-3"
            >
              + Nuevo Plan de Tratamiento
            </Button>
          </div>

          {errorTratamiento && (
            <Alert tono="error" titulo="No se pudo actualizar el tratamiento" onCerrar={() => setErrorTratamiento(null)}>
              {errorTratamiento}
            </Alert>
          )}

          {tratamientos.length === 0 ? (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-surface-container text-on-surface-variant">
                📋
              </div>
              <div className="space-y-1">
                <h3 className="font-display text-base font-bold text-on-surface">
                  No hay planes de tratamiento registrados
                </h3>
                <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                  Elabore una propuesta clínica con procedimientos del catálogo para iniciar el tratamiento de este paciente.
                </p>
              </div>
              <Button
                variant="accion"
                onClick={() => setModalTratamientoAbierto(true)}
              >
                Crear Primer Tratamiento
              </Button>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-6">
              {tratamientos.map((t) => {
                const progresoSesiones = t.totalSesiones
                  ? Math.round(((t.sesionesRealizadas ?? 0) / t.totalSesiones) * 100)
                  : 0

                return (
                  <section
                    key={t.id}
                    className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-card space-y-4"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 pb-3 border-b border-outline-variant/30">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <h3 className="font-display text-lg font-bold text-on-surface">
                            {t.nombre}
                          </h3>
                          <Badge tono={t.estado === 'COMPLETADO' || t.estado === 'TERMINADO' ? 'primario' : t.estado === 'INTERRUMPIDO' ? 'peligro' : t.estado === 'EN_PROCESO' ? 'primario' : 'neutro'}>
                            {t.estado}
                          </Badge>
                          {t.aceptadoPorPaciente && (
                            <Badge tono="primario">Presupuesto Aceptado</Badge>
                          )}
                          {(t.saldoPendiente ?? 0) <= 0 &&
                            (t.sesionesRealizadas ?? 0) < (t.totalSesiones ?? 1) &&
                            !['TERMINADO', 'COMPLETADO', 'INTERRUMPIDO', 'CANCELADO'].includes(t.estado ?? '') && (
                              <Badge tono="accion">Pagado · sesiones pendientes</Badge>
                            )}
                        </div>
                        {t.motivoCierre && (
                          <p className="text-xs text-on-surface-variant italic mt-1">
                            Cierre: {t.motivoCierre}
                          </p>
                        )}
                        <p className="text-xs text-on-surface-variant mt-1">
                          Responsable: <strong className="text-on-surface">{t.especialistaNombre ?? 'Por asignar'}</strong> · Modalidad: {t.modalidadPago === 'CUOTAS' ? 'Pago en cuotas' : t.modalidadPago}{t.sedeNombre ? ` · ${t.sedeNombre}` : ''}
                        </p>
                        {t.diagnostico && (
                          <p className="text-xs text-primary font-medium mt-1">
                            Diagnóstico: {t.diagnostico}
                          </p>
                        )}
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        {!t.aceptadoPorPaciente && (
                          <Button
                            variant="accion"
                            className="text-xs py-1 px-3"
                            disabled={aceptarTratamientoMutation.isPending}
                            onClick={() =>
                              aceptarTratamientoMutation.mutate({
                                id: t.id!,
                                dto: { aceptado: true, observaciones: 'Aceptado por paciente' },
                              })
                            }
                          >
                            ✓ Aceptar Presupuesto
                          </Button>
                        )}
                        {puede(sesion?.usuario.rol, 'gestionarTratamiento') &&
                          t.estado === 'EN_PROCESO' &&
                          (t.sesionesRealizadas ?? 0) < (t.totalSesiones ?? 1) && (
                            <Button
                              variant="secundario"
                              size="sm"
                              disabled={actualizarTratamientoMutation.isPending}
                              onClick={() => registrarSesion(t)}
                            >
                              +1 Sesión realizada
                            </Button>
                          )}
                        {puede(sesion?.usuario.rol, 'gestionarTratamiento') &&
                          !['TERMINADO', 'COMPLETADO', 'INTERRUMPIDO', 'CANCELADO'].includes(t.estado ?? '') && (
                            <Button
                              variant="secundario"
                              size="sm"
                              className="text-xs py-1 px-3"
                              onClick={() => setPlanParaCerrar(t)}
                            >
                              Cerrar plan
                            </Button>
                          )}
                        {puede(sesion?.usuario.rol, 'gestionarTratamiento') &&
                          t.estado !== 'COMPLETADO' &&
                          t.estado !== 'TERMINADO' &&
                          t.estado !== 'INTERRUMPIDO' &&
                          t.estado !== 'CANCELADO' &&
                          (t.sesionesRealizadas ?? 0) >= (t.totalSesiones ?? 1) && (
                            <Button
                              variant="primario"
                              size="sm"
                              disabled={actualizarTratamientoMutation.isPending}
                              onClick={() => finalizarTratamiento(t)}
                            >
                              Finalizar Tratamiento
                            </Button>
                          )}
                        {(t.saldoPendiente ?? 0) > 0 && (
                          <Button
                            variant="primario"
                            className="text-xs py-1 px-3"
                            onClick={() => {
                              setTratamientoParaCobro(t)
                              setModalPagoAbierto(true)
                            }}
                          >
                            Abonar Cuota
                          </Button>
                        )}
                        <Button
                          variant="secundario"
                          className="text-xs py-1 px-3 gap-1"
                          disabled={(t.saldoPendiente ?? 0) > 0}
                          onClick={() => {
                            if ((t.saldoPendiente ?? 0) > 0) return
                            setComprobanteTratamiento(t)
                          }}
                          title={
                            (t.saldoPendiente ?? 0) > 0
                              ? `Falta pagar ${formatSoles(t.saldoPendiente ?? 0)} para emitir el comprobante`
                              : 'Imprimir el comprobante de pago de este tratamiento'
                          }
                          aria-label={
                            (t.saldoPendiente ?? 0) > 0
                              ? `Falta pagar ${formatSoles(t.saldoPendiente ?? 0)} para emitir el comprobante`
                              : 'Comprobante de pago'
                          }
                        >
                          <span>🖨️</span>
                          <span>Comprobante de pago</span>
                        </Button>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-mono">
                      <div className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                        <span className="text-on-surface-variant block text-xs">Costo Total:</span>
                        <span className="font-bold text-on-surface">{formatSoles(t.costoTotal ?? 0)}</span>
                      </div>
                      <div className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                        <span className="text-on-surface-variant block text-xs">Descuento:</span>
                        <span className="font-bold text-emerald-600">-{formatSoles(t.descuentoTotal ?? 0)}</span>
                      </div>
                      <div className="rounded border border-outline-variant/40 bg-surface-container-low p-2.5">
                        <span className="text-on-surface-variant block text-xs">Monto Pagado:</span>
                        <span className="font-bold text-emerald-700">{formatSoles(t.montoPagado ?? 0)}</span>
                      </div>
                      <div className="rounded border-2 border-primary/30 bg-primary/5 p-2.5">
                        <span className="text-primary block text-xs font-sans font-semibold">Saldo Pendiente:</span>
                        <span className={`font-bold text-sm ${esSaldoAFavor(t.saldoPendiente) ? 'text-emerald-700' : 'text-primary'}`}>
                          {formatoSaldo(t.saldoPendiente)}
                        </span>
                      </div>
                    </div>

                    {t.nombre?.toLowerCase().includes('ortodoncia') && (
                      <div className="flex flex-wrap items-center justify-between gap-1 p-2 rounded-lg bg-primary/5 border border-primary/20 text-xs">
                        <span className="font-semibold text-primary">
                          🦷 Saldo Pendiente de Ortodoncia:
                        </span>
                        <span className="text-on-surface-variant font-mono">
                          {formatSoles(t.costoTotal ?? 0)} (Costo) - {formatSoles(t.montoPagado ?? 0)} (Pagado) = <strong className={`font-bold ${esSaldoAFavor(t.saldoPendiente) ? 'text-emerald-700' : 'text-primary'}`}>{formatoSaldo(t.saldoPendiente)}</strong>
                        </span>
                      </div>
                    )}

                    <CronogramaCuotas cuotas={t.cuotas} vencidas={t.cuotasVencidas} />

                    <div className="space-y-1.5 pt-1">
                      <div className="flex justify-between text-xs">
                        <span className="text-on-surface-variant">
                          Sesiones clínicas: <strong>{t.sesionesRealizadas ?? 0}</strong> de <strong>{t.totalSesiones ?? 1}</strong> realizadas
                        </span>
                        <span className="font-bold text-primary">{progresoSesiones}%</span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-surface-container overflow-hidden">
                        <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${progresoSesiones}%` }} />
                      </div>
                      <CambiarSesionesPlan plan={t} puedeEditar={['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'].includes(sesion?.usuario.rol ?? '')} />
                    </div>

                    {t.items && t.items.length > 0 && (() => {
                      const clasificarFase = (it: typeof t.items[0]) => {
                        const obs = it.observaciones || ''
                        if (obs.includes('FASE_1')) return 'FASE_1'
                        if (obs.includes('FASE_3')) return 'FASE_3'
                        if (obs.includes('FASE_2')) return 'FASE_2'
                        return 'FASE_2'
                      }

                      const fasesConfig = [
                        { id: 'FASE_2', titulo: 'Procedimientos', icono: '', colorFondo: '' },
                      ]

                      const itemsPorFase = fasesConfig.map((fase) => {
                        const itemsFase = (t.items ?? []).filter((it) => clasificarFase(it) === fase.id)
                        const subtotal = itemsFase.reduce(
                          (acc, it) => acc + (Number(it.precioBase ?? 0) * (it.cantidad ?? 1)) - Number(it.descuento ?? 0),
                          0
                        )
                        return { ...fase, items: itemsFase, subtotal }
                      }).filter((f) => f.items.length > 0)

                      return (
                        <div className="space-y-3 pt-2">
                          <div className="flex items-center justify-between">
                            <h4 className="text-xs font-bold uppercase tracking-wider text-on-surface">
                              Procedimientos del plan ({t.items.length})
                            </h4>
                          </div>

                          <div className="space-y-3">
                            {itemsPorFase.map((fase) => (
                              <div
                                key={fase.id}
                                className={`rounded-lg border border-outline-variant/50 overflow-hidden ${fase.colorFondo}`}
                              >
                                <div className="flex items-center justify-between p-2.5 bg-surface-container-low/70 border-b border-outline-variant/40 text-xs">
                                  <div className="flex items-center gap-2">
                                    {fase.icono && <span className="text-sm">{fase.icono}</span>}
                                    <span className="font-bold text-on-surface">{fase.titulo}</span>
                                    <span className="text-xs text-on-surface-variant">({fase.items.length} ítems)</span>
                                  </div>
                                  <div className="flex items-center gap-1.5 font-mono">
                                    <span className="text-xs text-on-surface-variant">Subtotal:</span>
                                    <span className="font-bold text-on-surface">{formatSoles(fase.subtotal)}</span>
                                  </div>
                                </div>

                                <div className="overflow-x-auto bg-surface-container-lowest">
                                  <table className="w-full text-left text-xs border-collapse">
                                    <thead className="bg-surface-container-low/40 text-on-surface-variant text-xs">
                                      <tr>
                                        <th className="p-2">Procedimiento</th>
                                        <th className="p-2">Pieza FDI</th>
                                        <th className="p-2 text-right">Cant.</th>
                                        <th className="p-2 text-right">Precio Base</th>
                                        <th className="p-2 text-right">Descuento</th>
                                        <th className="p-2 text-right">Total Ítem</th>
                                      </tr>
                                    </thead>
                                    <tbody className="divide-y divide-outline">
                                      {fase.items.map((it) => {
                                        const totalItem = (Number(it.precioBase ?? 0) * (it.cantidad ?? 1)) - Number(it.descuento ?? 0)
                                        return (
                                          <tr key={it.id}>
                                            <td className="p-2 font-medium text-on-surface">{it.nombreProcedimiento}</td>
                                            <td className="p-2 font-mono text-on-surface-variant">{it.piezaFdi ?? '-'}</td>
                                            <td className="p-2 text-right font-mono">{it.cantidad}</td>
                                            <td className="p-2 text-right font-mono">{formatSoles(it.precioBase)}</td>
                                            <td className="p-2 text-right font-mono text-emerald-600">
                                              {(it.descuento ?? 0) > 0 ? `-${formatSoles(it.descuento)}` : '-'}
                                            </td>
                                            <td className="p-2 text-right font-mono font-semibold text-on-surface">
                                              {formatSoles(totalItem)}
                                            </td>
                                          </tr>
                                        )
                                      })}
                                    </tbody>
                                  </table>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )
                    })()}
                  </section>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* ─────────── Pestaña: Pagos y Saldo ─────────── */}
      {pestañaActiva === 'tratamientos' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Historial de Pagos & Comprobantes
              </h2>
              <p className="text-xs text-on-surface-variant">
                Recibos de caja y abonos registrados a la cuenta del paciente
              </p>
            </div>
            <Button
              variant="accion"
              onClick={() => {
                setTratamientoParaCobro(undefined)
                setModalPagoAbierto(true)
              }}
              className="text-xs py-1.5 px-3"
            >
              + Registrar Cobro / Abono
            </Button>
          </div>

          {pagos.length === 0 ? (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-surface-container text-on-surface-variant">
                🧾
              </div>
              <div className="space-y-1">
                <h3 className="font-display text-base font-bold text-on-surface">
                  No hay comprobantes de pago registrados
                </h3>
                <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                  Aún no se han emitido recibos ni registrado abonos para este paciente.
                </p>
              </div>
              <Button
                variant="accion"
                onClick={() => {
                  setTratamientoParaCobro(undefined)
                  setModalPagoAbierto(true)
                }}
              >
                Registrar Primer Cobro
              </Button>
            </div>
          ) : (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest shadow-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                    <tr>
                      <th className="py-2.5 px-4">Comprobante</th>
                      <th className="py-2.5 px-4">Fecha y Hora</th>
                      <th className="py-2.5 px-4">Tratamiento / Concepto</th>
                      <th className="py-2.5 px-4">Método</th>
                      <th className="py-2.5 px-4 text-right">Monto (S/)</th>
                      <th className="py-2.5 px-4">Estado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline">
                    {pagos.map((p) => (
                      <tr key={p.id} className="hover:bg-surface-container-low/50">
                        <td className="py-2.5 px-4 font-mono font-bold text-primary">
                          {p.comprobante}
                        </td>
                        <td className="py-2.5 px-4 text-on-surface-variant font-mono">
                          {formatFechaHora(p.pagadoEn)}
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
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ─────────── Pestaña: Consentimientos (Fase 5) ─────────── */}
      {pestañaActiva === 'estudios' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-display text-xl font-bold text-on-surface">
                Consentimientos Informados & Protección de Datos
              </h2>
              <p className="text-xs text-on-surface-variant">
                Documentos legales debidamente suscritos conforme a la Ley N° 29733 y normativa de salud
              </p>
            </div>
            <Button
              variant="accion"
              onClick={() => setModalConsentimientoAbierto(true)}
              className="text-xs py-1.5 px-3"
            >
              + Firmar Consentimiento
            </Button>
          </div>

          {consentimientos.length === 0 ? (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest p-12 text-center space-y-4 shadow-card">
              <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-surface-container text-on-surface-variant">
                📜
              </div>
              <div className="space-y-1">
                <h3 className="font-display text-base font-bold text-on-surface">
                  No hay consentimientos informados suscritos
                </h3>
                <p className="text-xs text-on-surface-variant max-w-sm mx-auto">
                  Seleccione una de las plantillas oficiales (Ley 29733, Cirugía, Endodoncia o General) para su firma legal.
                </p>
              </div>
              <Button
                variant="accion"
                onClick={() => setModalConsentimientoAbierto(true)}
              >
                Suscribir Primer Consentimiento
              </Button>
            </div>
          ) : (
            <div className="rounded-card border border-outline-variant/40 bg-surface-container-lowest shadow-card overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead className="bg-surface-container-low text-on-surface-variant font-medium">
                    <tr>
                      <th className="py-2.5 px-4">Documento / Consentimiento</th>
                      <th className="py-2.5 px-4">Tipo</th>
                      <th className="py-2.5 px-4">Firmante</th>
                      <th className="py-2.5 px-4">DNI / Relación</th>
                      <th className="py-2.5 px-4">Fecha y Hora</th>
                      <th className="py-2.5 px-4">Estado</th>
                      <th className="py-2.5 px-4 text-right">Acciones</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline">
                    {consentimientos.map((c) => (
                      <tr key={c.id} className="hover:bg-surface-container-low/50">
                        <td className="py-2.5 px-4 font-bold text-on-surface">
                          {c.titulo || 'Consentimiento Informado'}
                        </td>
                        <td className="py-2.5 px-4">
                          <Badge tono="neutro">{c.tipo}</Badge>
                        </td>
                        <td className="py-2.5 px-4 font-medium text-on-surface">
                          {c.nombreFirmante || paciente.nombreCompleto}
                        </td>
                        <td className="py-2.5 px-4 font-mono text-on-surface-variant">
                          {c.documentoFirmante || paciente.dni} ({c.parentescoFirmante || 'Titular'})
                        </td>
                        <td className="py-2.5 px-4 font-mono text-on-surface-variant">
                          {formatFechaHora(c.firmadoEn)}
                        </td>
                        <td className="py-2.5 px-4">
                          <Badge tono={c.activo !== false ? 'primario' : 'peligro'}>
                            {c.activo !== false ? '✓ Firmado y Vigente' : 'Revocado'}
                          </Badge>
                        </td>
                        <td className="py-2.5 px-4 text-right">
                          <Button
                            variant="secundario"
                            className="text-xs py-1 px-2.5"
                            onClick={() => setConsentimientoSeleccionado(c)}
                          >
                            👁️ Ver Documento & Firma
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ─────────── Modales Fase 4 (Tratamiento y Caja) ─────────── */}
      <NuevoTratamientoModal
        abierto={modalTratamientoAbierto}
        alCerrar={() => {
          setModalTratamientoAbierto(false)
          setItemsTratamientoDesdeOdonto(undefined)
        }}
        pacienteId={paciente.id!}
        pacienteNombre={paciente.nombreCompleto}
        sedeId={paciente.sedeId ?? 'sede-chilca-1'}
        itemsIniciales={itemsTratamientoDesdeOdonto}
        alGuardar={() => {
          recargarTratamientos()
          refetch()
          void qc.invalidateQueries({ queryKey: ['tratamientos'] })
          void qc.invalidateQueries({ queryKey: ['pacientes'] })
          void qc.invalidateQueries({ queryKey: ['citas'] })
          void qc.invalidateQueries({ queryKey: ['reportes'] })
        }}
      />

      <RegistrarPagoModal
        abierto={modalPagoAbierto}
        alCerrar={() => {
          setModalPagoAbierto(false)
          setTratamientoParaCobro(undefined)
        }}
        sedeId={paciente.sedeId ?? 'sede-chilca-1'}
        pacienteInicialId={paciente.id!}
        pacienteInicialNombre={paciente.nombreCompleto}
        tratamientoInicialId={tratamientoParaCobro?.id ?? tratamientos.find((t) => (t.saldoPendiente ?? 0) > 0)?.id}
        saldoPendienteSugerido={tratamientoParaCobro?.saldoPendiente ?? tratamientos.find((t) => (t.saldoPendiente ?? 0) > 0)?.saldoPendiente}
        alGuardar={() => {
          recargarPagos()
          recargarTratamientos()
          refetch()
          void qc.invalidateQueries({ queryKey: ['pagos'] })
          void qc.invalidateQueries({ queryKey: ['tratamientos'] })
          void qc.invalidateQueries({ queryKey: ['pacientes'] })
          void qc.invalidateQueries({ queryKey: ['caja'] })
          void qc.invalidateQueries({ queryKey: ['citas'] })
          void qc.invalidateQueries({ queryKey: ['reportes'] })
        }}
      />

      {/* Nueva cita para este paciente sin salir de la ficha; al guardar, la próxima cita del Resumen se refresca sola. */}
      <NuevaCitaDialog
        prefijo={nuevaCita}
        onCerrar={() => setNuevaCita(null)}
        onCreada={() => {
          refetch()
          recargarTratamientos()
          recargarPagos()
          void qc.invalidateQueries({ queryKey: ['citas'] })
          void qc.invalidateQueries({ queryKey: ['pacientes'] })
        }}
      />

      <EmitirRecetaModal
        abierto={recetaAbierta}
        alCerrar={() => setRecetaAbierta(false)}
        paciente={{
          id: paciente.id!,
          nombreCompleto: paciente.nombreCompleto ?? '',
          dni: paciente.dni,
          edad: paciente.edad,
          telefono: paciente.telefono,
        }}
      />

      <NuevaNotaEvolucionModal
        abierto={modalNotaAbierto}
        alCerrar={() => {
          setModalNotaAbierto(false)
          setNotaReferenciaIdAdenda(undefined)
        }}
        pacienteId={paciente.id!}
        pacienteNombre={paciente.nombreCompleto}
        notaReferenciaId={notaReferenciaIdAdenda}
        alGuardar={() => {
          recargarNotas()
          refetch()
        }}
      />

      <ActualizarAntecedentesModal
        abierto={modalAntecedentesAbierto}
        alCerrar={() => setModalAntecedentesAbierto(false)}
        pacienteId={paciente.id!}
        pacienteNombre={paciente.nombreCompleto}
        alergiasIniciales={paciente.alergias}
        antecedentesIniciales={paciente.antecedentesMedicos}
        gestanteInicial={paciente.gestante}
        semanasIniciales={paciente.semanasGestacion}
        alGuardar={() => {
          refetch()
        }}
      />

      <EditarPiezaOdontogramaModal
        abierto={modalPiezaAbierto}
        alCerrar={() => setModalPiezaAbierto(false)}
        pacienteId={paciente.id!}
        piezaInicialFdi={piezaSeleccionada}
        registroActual={registroPiezaSeleccionada}
        alGuardar={() => {
          recargarOdontograma()
        }}
      />

      <SubirRadiografiaModal
        abierto={modalSubirRadioAbierto}
        alCerrar={() => setModalSubirRadioAbierto(false)}
        pacienteId={paciente.id!}
        pacienteNombre={paciente.nombreCompleto}
        alGuardar={() => {
          recargarRadiografias()
        }}
      />

      <VisorRadiografiaModal
        abierto={Boolean(radiografiaSeleccionada)}
        alCerrar={() => setRadiografiaSeleccionada(null)}
        radiografia={radiografiaSeleccionada}
      />

      <FirmarConsentimientoModal
        abierto={modalConsentimientoAbierto}
        alCerrar={() => setModalConsentimientoAbierto(false)}
        pacienteId={paciente.id!}
        pacienteNombre={paciente.nombreCompleto ?? ''}
        pacienteDni={paciente.dni ?? ''}
        alGuardar={() => {
          recargarConsentimientos()
          refetch()
        }}
      />

      <VisorConsentimientoModal
        abierto={Boolean(consentimientoSeleccionado)}
        alCerrar={() => setConsentimientoSeleccionado(null)}
        consentimiento={consentimientoSeleccionado}
        pacienteNombre={paciente?.nombreCompleto}
      />

      <ImprimirComprobanteModal
        abierto={Boolean(comprobanteTratamiento)}
        alCerrar={() => setComprobanteTratamiento(null)}
        tratamiento={comprobanteTratamiento}
        pacienteNombre={paciente?.nombreCompleto}
        pacienteDni={paciente?.dni}
        sedeNombre={comprobanteTratamiento?.sedeNombre ?? 'Sede Central'}
        sedeDireccion={datosSedeComprobante.direccion}
        sedeTelefono={datosSedeComprobante.telefono}
        comprobantes={pagos
          .filter((p) => !p.anulado && p.tratamientoId === comprobanteTratamiento?.id)
          .map((p) => p.comprobante)
          .filter(Boolean)}
        mediosDePago={[
          ...new Set(
            pagos
              .filter((p) => !p.anulado && p.tratamientoId === comprobanteTratamiento?.id)
              .map((p) => nombreMetodoPago(p.metodoPago)),
          ),
        ]}
      />

      <CerrarPlanModal
        abierto={Boolean(planParaCerrar)}
        alCerrar={() => setPlanParaCerrar(null)}
        tratamiento={planParaCerrar}
        alConfirmar={handleCerrarPlan}
      />
    </div>
  )
}
