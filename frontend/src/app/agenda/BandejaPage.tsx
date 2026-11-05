import { EsperaDeHuecos } from './EsperaDeHuecos'
import { Link } from '@tanstack/react-router'
import {
  AlertTriangle,
  Armchair,
  CalendarCheck,
  CalendarDays,
  CalendarRange,
  Clock,
  DoorOpen,
  ExternalLink,
  FlaskConical,
  Globe,
  Hourglass,
  MessageCircle,
  Wallet,
  Phone,
  RefreshCw,
  Sparkles,
  UserX,
} from 'lucide-react'
import { useState, useId } from 'react'
import {
  Button,
  Card,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  Eyebrow,
  Alert,
  Input,
  StatCard,
  buttonVariants,
  toast,
} from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatFecha, hoyEnLima } from '@/shared/format'
import { AvisoCajaCerrada } from '../caja/AvisoCajaCerrada'
import { useCajaCerrada } from '../caja/useCajaCerrada'
import { AccionesCita } from './AccionesCita'
import {
  useBandejaTareas,
  useCitasDelDia,
  useListaEspera,
  useIniciarAtencion,
  useProcesarExpiradas,
  useRegistrarAdelanto,
  useRegistrarContacto,
  useReservasWebPendientes,
  type Cita,
  type ItemTareaBandeja,
} from './api-agenda'
import { CitaFila } from './CitaFila'
import { capitalizar, horaInstanteEnLima, minutosEntreInstantes, nombreDia, sumarDias } from './fechas'
import { Consulta, Seccion } from './Seccion'
import { DialogosCita } from './DialogosCita'
import { useDialogosCita } from './useDialogosCita'
import { mensajeError } from './errores'

function contar(citas: Cita[] | undefined, estado: string) {
  return citas?.filter((c) => c.estado === estado).length ?? 0
}

function TarjetaCumpleanos({
  titulo,
  lista,
  mostrarWhatsapp,
  sedeNombre,
}: {
  titulo: string
  lista: ItemTareaBandeja[]
  mostrarWhatsapp: boolean
  sedeNombre?: string
}) {
  if (lista.length === 0) return null

  return (
    <Card className="gap-3 p-5 border border-purple-200 bg-purple-50/40 dark:bg-purple-950/20">
      <div className="flex items-center justify-between border-b border-purple-200/60 pb-2">
        <h3 className="font-display text-sm font-bold text-purple-950 dark:text-purple-100 flex items-center gap-1.5">
          <Sparkles className="size-4 text-purple-600" />
          {titulo}
        </h3>
        <span className="rounded-pill bg-purple-200 px-2 py-0.5 text-xs font-bold text-purple-900">
          {lista.length} {lista.length === 1 ? 'paciente' : 'pacientes'}
        </span>
      </div>
      {mostrarWhatsapp && (
        <p className="text-xs text-on-surface-variant">
          Pacientes con consentimiento activo de fidelización en {sedeNombre ?? 'la sede'}.
        </p>
      )}
      <ul className="flex flex-col gap-2">
        {lista.map((paciente) => (
          <li key={paciente.pacienteId ?? paciente.id} className="rounded-xl bg-white dark:bg-surface-container p-3 border border-purple-100 shadow-xs">
            <div className="flex items-start justify-between gap-1">
              <div>
                <strong className="block text-xs text-on-surface">{paciente.pacienteNombre}</strong>
                {paciente.pacienteDni && <span className="text-xs text-on-surface-variant">DNI {paciente.pacienteDni}</span>}
                {!mostrarWhatsapp && paciente.pacienteTelefono && (
                  <span className="block text-xs text-on-surface-variant">Teléfono: {paciente.pacienteTelefono}</span>
                )}
              </div>
              {mostrarWhatsapp && (
                <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-xs font-bold text-emerald-800">
                  Promo Activa
                </span>
              )}
            </div>
            {mostrarWhatsapp && paciente.enlaceWhatsapp && (
              <a
                href={paciente.enlaceWhatsapp}
                target="_blank"
                rel="noreferrer"
                className="mt-2.5 flex items-center justify-center gap-1.5 rounded-pill bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-emerald-700 transition-colors"
              >
                <MessageCircle className="size-3.5" />
                Enviar saludo WhatsApp
              </a>
            )}
          </li>
        ))}
      </ul>
    </Card>
  )
}

/**
 * B2 · Bandeja del día & Mesa de Operaciones (Stitch Screen B2 & Backend v2 Sección 5).
 * - Citas de hoy y mañana con métricas en tiempo real.
 * - Bandeja de tareas de WhatsApp (24h antes) con enlace wa.me y registro de resultado.
 * - Llamadas de hoy para confirmar citas inminentes.
 * - Citas con adelanto Yape por validar y cuenta regresiva de expiración.
 * - Alerta clínica preventiva para pacientes con 2 o más inasistencias.
 * - Saludos de cumpleaños de hoy con promociones (Ley 29733).
 */
export function BandejaPage() {
  const uid = useId()
  const sesion = useSesion()
  const sedeId = sesion?.sedeActivaId ?? null
  const hoy = hoyEnLima()
  const manana = sumarDias(hoy, 1)

  const citasHoy = useCitasDelDia(sedeId, hoy)
  const citasManana = useCitasDelDia(sedeId, manana)
  const reservasWeb = useReservasWebPendientes(sedeId)
  const listaEspera = useListaEspera(sedeId)
  const bandejaTareas = useBandejaTareas(sedeId, hoy)
  const dialogos = useDialogosCita()
  const cajaCerrada = useCajaCerrada(sedeId)

  const procesarExpiradas = useProcesarExpiradas()
  const registrarContacto = useRegistrarContacto()
  const registrarAdelanto = useRegistrarAdelanto()
  const iniciarAtencion = useIniciarAtencion()

  // Estado del modal de registro de adelanto
  const [adelantoModal, setAdelantoModal] = useState<{
    citaId: string
    pacienteNombre?: string
    monto: number
  } | null>(null)
  const [adelantoForm, setAdelantoForm] = useState({
    medioPago: 'YAPE',
    monto: '30.00',
    comprobanteUrl: '',
  })
  const [mensajeExpiradas, setMensajeExpiradas] = useState<string | null>(null)

  if (!sesion) return null
  const sede = sesion.usuario.sedes.find((s) => s.id === sedeId)
  const primerNombre = sesion.usuario.nombre.split(/\s+/)[0]
  const datosHoy = citasHoy.data
  const activasHoy = datosHoy?.filter((c) => c.estado !== 'CANCELADA') ?? []
  const pendientesManana = citasManana.data?.filter((c) => c.estado === 'PROGRAMADA') ?? []

  const tareas = bandejaTareas.data
  const listaTareas: ItemTareaBandeja[] = tareas?.tareas ?? []
  const tareasDeHoy = listaTareas.filter((t) => t.tipo !== 'RECORDATORIO_CUMPLEANOS')
  const adelantosPorValidar = tareasDeHoy.filter((t) => t.tipo === 'ADELANTO_POR_EXPIRAR')
  const recordatoriosManana = tareasDeHoy.filter((t) => t.tipo === 'RECORDATORIO_24H')
  const llamadasHoy = tareasDeHoy.filter((t) => t.tipo === 'LLAMADA_HOY')
  const cuotasVencidas = tareasDeHoy.filter((t) => t.tipo === 'CUOTA_VENCIDA')
  const huecosLibres = tareasDeHoy.filter((t) => t.tipo === 'HUECO_DISPONIBLE')
  const cumpleanosHoy = tareasDeHoy.filter((t) => t.tipo === 'SALUDO_CUMPLEANOS')
  const cumpleanosManana = listaTareas.filter((t) => t.tipo === 'RECORDATORIO_CUMPLEANOS')
  const tareasPasarAlSillon = tareasDeHoy.filter((t) => t.tipo === 'PASAR_AL_SILLON')
  const alertasInasistencias = tareasDeHoy.filter((t) => Boolean(t.avisoInasistencias))

  const handleProcesarExpiradas = async () => {
    try {
      const res = await procesarExpiradas.mutateAsync()
      setMensajeExpiradas(
        `Se procesaron las citas por expirar: ${(res as { totalExpiradas?: number })?.totalExpiradas ?? 0} citas expiradas y liberadas.`
      )
      setTimeout(() => setMensajeExpiradas(null), 5000)
    } catch {
      setMensajeExpiradas('Error al procesar citas expiradas.')
    }
  }

  const handleRegistrarContacto = (citaId: string, medio: 'WHATSAPP' | 'LLAMADA', resultado: 'CONFIRMO' | 'PIDIO_REPROGRAMAR' | 'CANCELO' | 'NO_RESPONDE' | 'MENSAJE_ENVIADO') => {
    void registrarContacto.mutateAsync({
      id: citaId,
      cuerpo: {
        medio,
        resultado,
        notas: `Registrado desde Bandeja de Operaciones por ${primerNombre}`,
      },
    })
  }

  const AVISO_RESULTADO = {
    CONFIRMO: 'Confirmada: ya no aparece por confirmar.',
    PIDIO_REPROGRAMAR: 'Registrado. Elige la nueva fecha y hora.',
    CANCELO: 'Registrado. Indica el motivo para cancelar la cita y liberar el horario.',
    NO_RESPONDE: 'Registrado: no respondió. Quedó marcada para volver a intentar.',
  } as const

  /**
   * Resultado del contacto con el paciente. Siempre queda registrado y avisa; si pidió reprogramar o canceló, abre de una
   * vez el diálogo para hacerlo (la cita sigue ocupando el horario hasta que se reprograma o se cancela).
   */
  const manejarResultado = (citaId: string, medio: 'WHATSAPP' | 'LLAMADA', resultado: keyof typeof AVISO_RESULTADO) => {
    handleRegistrarContacto(citaId, medio, resultado)
    toast(AVISO_RESULTADO[resultado])
    const cita = [...(citasManana.data ?? []), ...(citasHoy.data ?? [])].find((c) => c.id === citaId)
    if (!cita) return
    if (resultado === 'PIDIO_REPROGRAMAR') dialogos.reprogramar(cita)
    if (resultado === 'CANCELO') dialogos.cancelar(cita)
  }

  const handleConfirmarAdelanto = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!adelantoModal) return
    try {
      await registrarAdelanto.mutateAsync({
        id: adelantoModal.citaId,
        cuerpo: {
          medioPago: adelantoForm.medioPago,
          monto: Number(adelantoForm.monto),
          comprobanteUrl: adelantoForm.comprobanteUrl.trim() || undefined,
        },
      })
      setAdelantoModal(null)
      setAdelantoForm({ medioPago: 'YAPE', monto: '30.00', comprobanteUrl: '' })
    } catch {
      // Error manejado
    }
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6">
      {/* Barra de operación del día (se muestra dentro del Inicio) */}
      <section aria-label="Operación del día" className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-display text-xl font-bold text-primary">Operación del día</h2>
          <p className="text-sm text-on-surface-variant">
            {capitalizar(nombreDia(hoy))} {formatFecha(hoy)}
            {sede ? ` · ${sede.nombre}` : ''}. Citas por confirmar, reservas web, recordatorios y sala de espera.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secundario"
            size="sm"
            onClick={() => void handleProcesarExpiradas()}
            disabled={procesarExpiradas.isPending}
            className="gap-1.5"
          >
            <RefreshCw className={`size-3.5 ${procesarExpiradas.isPending ? 'animate-spin' : ''}`} />
            Procesar citas expiradas
          </Button>
          <Link to="/app/agenda" className={buttonVariants({ variant: 'secundario', size: 'sm' })}>
            <CalendarRange aria-hidden className="size-3.5" />
            Ver agenda
          </Link>
        </div>
        {mensajeExpiradas && (
          <div className="w-full rounded-panel bg-surface-container px-4 py-2 text-xs font-medium text-on-surface">
            {mensajeExpiradas}
          </div>
        )}
      </section>

      {/* Caja cerrada: avisa antes de que falle un cobro o un anticipo */}
      {cajaCerrada && <AvisoCajaCerrada />}

      {/* Alerta de pacientes con 2+ inasistencias (Stitch B2 / Sección 5) */}
      {alertasInasistencias.length > 0 && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-card border-2 border-[#f59e0b] bg-[#fffbeb] p-5 text-[#78350f] shadow-sm"
        >
          <div className="flex items-center gap-2.5 font-bold tracking-tight text-[#78350f]">
            <AlertTriangle className="size-5 text-[#d97706]" />
            <span className="text-sm uppercase tracking-wider">Aviso Clínico Crítico — Bloqueo Preventivo</span>
          </div>
          <div className="space-y-2 text-xs">
            {alertasInasistencias.map((alerta) => (
              <div key={alerta.citaId ?? alerta.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-[#fde68a] pt-2">
                <span>
                  Paciente con <strong>{alerta.totalInasistencias ?? 2} inasistencias previas detectadas</strong> agendado para hoy a las <strong>{alerta.horaCita?.slice(0, 5)}</strong>:{' '}
                  <strong className="text-[#78350f]">{alerta.pacienteNombre}</strong>. Requiere adelanto obligatorio previo a asignación de sillón.
                </span>
                <span className="rounded-pill bg-[#fef3c7] px-2.5 py-0.5 font-bold text-[#92400e] text-xs border border-[#fde68a]">
                  Requiere adelanto
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tarjetas KPI Superiores */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          etiqueta="Total de citas hoy"
          icono={<CalendarDays />}
          cargando={citasHoy.isPending}
          valor={citasHoy.isError ? '—' : activasHoy.length}
          complemento={contar(datosHoy, 'CANCELADA') ? `${contar(datosHoy, 'CANCELADA')} canceladas` : 'sin cancelar'}
        />
        <StatCard
          etiqueta="Confirmadas"
          icono={<CalendarCheck />}
          cargando={citasHoy.isPending}
          valor={citasHoy.isError ? '—' : contar(datosHoy, 'CONFIRMADA')}
          complemento={`${contar(datosHoy, 'PROGRAMADA')} por confirmar`}
        />
        <StatCard
          etiqueta="En sala de espera"
          icono={<DoorOpen />}
          tonoIcono="villamax"
          cargando={listaEspera.isPending}
          valor={listaEspera.isError ? '—' : (listaEspera.data?.length ?? 0)}
          complemento="pacientes"
        />
        <StatCard
          etiqueta="No asistieron"
          icono={<UserX />}
          tonoIcono="accion"
          cargando={citasHoy.isPending}
          valor={citasHoy.isError ? '—' : contar(datosHoy, 'NO_ASISTIO')}
          complemento="hoy"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex min-w-0 flex-col gap-6">
          {/* Sección 1: Citas de Hoy */}
          <Seccion
            id="sec-hoy"
            titulo="Citas de hoy"
            icono={<CalendarDays />}
            contador={datosHoy ? `${activasHoy.length} citas` : undefined}
            descripcion={`${formatFecha(hoy)}${sede ? ` · ${sede.nombre}` : ''}`}
            acciones={
              <Link to="/app/agenda" className={buttonVariants({ variant: 'secundario', size: 'sm' })}>
                <CalendarRange aria-hidden />
                Ver agenda
              </Link>
            }
          >
            <Consulta
              consulta={citasHoy}
              cargando="Cargando las citas de hoy…"
              vacio={{ titulo: 'No hay citas para hoy', descripcion: 'Cuando se agenden citas en esta sede aparecerán aquí.' }}
            >
              {(citas) => (
                <ul className="flex flex-col gap-3">
                  {citas.map((c) => (
                    <CitaFila
                      key={c.id}
                      cita={c}
                      onVerDetalle={dialogos.verDetalle}
                      acciones={<AccionesCita cita={c} onCancelar={dialogos.cancelar} onReprogramar={dialogos.reprogramar} />}
                    />
                  ))}
                </ul>
              )}
            </Consulta>
          </Seccion>

          {tareasPasarAlSillon.length > 0 && (
            <Seccion
              id="sec-pasar-sillon"
              titulo="Pasar al sillón"
              icono={<Armchair />}
              tono="accion"
              contador={`${tareasPasarAlSillon.length} pacientes`}
              descripcion="Pacientes en sala de espera que aún no han iniciado atención."
            >
              {iniciarAtencion.isError && (
                <Alert tono="error" titulo="No se pudo iniciar la atención" onCerrar={() => iniciarAtencion.reset()}>
                  {mensajeError(iniciarAtencion.error)}
                </Alert>
              )}
              <ul className="flex flex-col gap-3">
                {tareasPasarAlSillon.map((item) => {
                  const horaLlegada = horaInstanteEnLima(item.llegadaEn)
                  return (
                    <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-panel bg-surface-container-low p-4">
                      <div>
                        <h3 className="font-bold text-on-surface">{item.pacienteNombre || 'Paciente sin nombre'}</h3>
                        <p className="text-sm text-on-surface-variant">
                          {horaLlegada ? `Espera en sala desde las ${horaLlegada}` : 'Espera en sala'}
                        </p>
                      </div>
                      {item.citaId && (
                        <Button
                          size="sm"
                          variant="primario"
                          disabled={iniciarAtencion.isPending}
                          onClick={async () => {
                            try {
                              await iniciarAtencion.mutateAsync({ id: item.citaId! })
                              toast(`Atención iniciada para ${item.pacienteNombre || 'el paciente'}`)
                            } catch {
                              // El error se presenta en la alerta de esta sección.
                            }
                          }}
                        >
                          <Armchair aria-hidden />
                          Pasar al sillón
                        </Button>
                      )}
                    </li>
                  )
                })}
              </ul>
            </Seccion>
          )}

          {sedeId && <EsperaDeHuecos sedeId={sedeId} huecos={huecosLibres} />}

          {cuotasVencidas.length > 0 && (
            <Seccion
              id="sec-cuotas"
              titulo="Cuotas vencidas"
              icono={<Wallet />}
              tono="accion"
              contador={`${cuotasVencidas.length} ${cuotasVencidas.length === 1 ? 'paciente' : 'pacientes'}`}
              descripcion="Planes en cuotas (ortodoncia) con una cuota vencida sin pagar."
            >
              <ul className="flex flex-col gap-3">
                {cuotasVencidas.map((item) => (
                  <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-panel border border-outline-variant/60 bg-surface-container-low p-4">
                    <div>
                      <h3 className="font-bold text-on-surface">{item.pacienteNombre}</h3>
                      <p className="text-xs text-on-surface-variant">{item.motivoConsulta}</p>
                    </div>
                    {item.enlaceWhatsapp && (
                      <a
                        href={item.enlaceWhatsapp}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-full bg-[#16a34a] px-3.5 py-1.5 text-xs font-bold text-white hover:bg-[#15803d]"
                      >
                        <MessageCircle className="size-3.5" />
                        Recordar por WhatsApp
                      </a>
                    )}
                  </li>
                ))}
              </ul>
            </Seccion>
          )}

          {/* Sección 2: Confirmar citas de mañana (WhatsApp 24h) */}
          <Seccion
            id="sec-manana"
            titulo="Confirmar citas de mañana"
            icono={<CalendarCheck />}
            tono="accion"
            contador={recordatoriosManana.length > 0 ? `${recordatoriosManana.length} pendientes` : (citasManana.data ? `${pendientesManana.length} pendientes` : undefined)}
            descripcion={`Citas programadas para el ${nombreDia(manana)} ${formatFecha(manana)} que aún no están confirmadas.`}
          >
            {recordatoriosManana.length > 0 ? (
              <ul className="flex flex-col gap-3">
                {recordatoriosManana.map((item) => (
                  <li key={item.citaId ?? item.id} className="flex flex-col gap-3 rounded-panel border border-outline-variant/60 bg-surface-container-low p-4 shadow-xs">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm font-black text-primary">{item.horaCita?.slice(0, 5)}</span>
                          <h3 className="font-bold text-on-surface">{item.pacienteNombre}</h3>
                          {item.pacienteDni && <span className="rounded bg-surface-container px-1.5 py-0.5 text-xs text-on-surface-variant">DNI {item.pacienteDni}</span>}
                          {item.estadoConfirmacion === 'MENSAJE_ENVIADO' && <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-xs font-bold text-emerald-800">Mensaje enviado</span>}
                          {item.estadoConfirmacion === 'NO_RESPONDE' && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-bold text-amber-800">No respondió: volver a intentar</span>}
                          {item.estadoConfirmacion === 'CANCELADA_POR_PACIENTE' && <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-bold text-red-800">Dijo que cancela: falta cancelar la cita</span>}
                        </div>
                        <p className="text-xs text-on-surface-variant">
                          {[item.doctorNombre, item.motivoConsulta].filter(Boolean).join(' · ')}
                        </p>
                      </div>

                      {item.enlaceWhatsapp && (
                        <a
                          href={item.enlaceWhatsapp}
                          target="_blank"
                          rel="noreferrer"
                          onClick={() => {
                            if (item.citaId && item.estadoConfirmacion !== 'MENSAJE_ENVIADO') handleRegistrarContacto(item.citaId, 'WHATSAPP', 'MENSAJE_ENVIADO')
                          }}
                          className="inline-flex items-center gap-1.5 rounded-full bg-[#16a34a] px-3.5 py-1.5 text-xs font-bold text-white hover:bg-[#15803d] active:scale-[0.98] transition-all shadow-sm"
                        >
                          <MessageCircle className="size-3.5" />
                          {item.estadoConfirmacion === 'MENSAJE_ENVIADO' ? 'Reenviar WhatsApp' : 'Enviar WhatsApp'}
                          <ExternalLink className="size-3 opacity-80" />
                        </a>
                      )}
                    </div>

                    {/* Acciones de resultado rápido */}
                    {item.citaId && (
                      <div className="flex flex-wrap items-center gap-1.5 border-t border-outline-variant/40 pt-2.5">
                        <span className="text-xs font-semibold text-on-surface-variant mr-1">Resultado:</span>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'WHATSAPP', 'CONFIRMO')}
                          className="rounded-full bg-[#ecfdf5] hover:bg-[#d1fae5] text-[#065f46] border border-[#a7f3d0] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          ✓ Confirmó
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'WHATSAPP', 'PIDIO_REPROGRAMAR')}
                          className="rounded-full bg-[#fffbeb] hover:bg-[#fef3c7] text-[#92400e] border border-[#fde68a] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          Pidió reprogramar
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'WHATSAPP', 'CANCELO')}
                          className="rounded-full bg-[#fef2f2] hover:bg-[#fee2e2] text-[#991b1b] border border-[#fecaca] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          Canceló
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'WHATSAPP', 'NO_RESPONDE')}
                          className="rounded-full bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#475569] border border-[#cbd5e1] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          No responde
                        </button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <Consulta
                consulta={citasManana}
                cargando="Cargando las citas de mañana…"
                filtro={(c) => c.estado === 'PROGRAMADA'}
                filas={2}
                vacio={{ titulo: 'Nada por confirmar', descripcion: 'Todas las citas de mañana ya están confirmadas.' }}
              >
                {(citas) => (
                  <ul className="flex flex-col gap-3">
                    {citas.map((c) => (
                      <CitaFila
                        key={c.id}
                        cita={c}
                        onVerDetalle={dialogos.verDetalle}
                        acciones={
                          <AccionesCita
                            cita={c}
                            soloEstados={['CONFIRMADA']}
                            onCancelar={dialogos.cancelar}
                            onReprogramar={dialogos.reprogramar}
                          />
                        }
                      />
                    ))}
                  </ul>
                )}
              </Consulta>
            )}
          </Seccion>

          {/* Sección 3: Llamadas hoy para confirmar (Stitch B2) */}
          {llamadasHoy.length > 0 && (
            <Seccion
              id="sec-llamadas"
              titulo="Llamar hoy para confirmar"
              icono={<Phone />}
              tono="accion"
              contador={`${llamadasHoy.length} pendientes`}
              descripcion="Pacientes de hoy con confirmación pendiente ordenados por hora de la cita."
            >
              <ul className="flex flex-col gap-3">
                {llamadasHoy.map((item) => (
                  <li key={item.citaId ?? item.id} className="flex flex-col gap-3 rounded-panel border border-[#fde68a] bg-[#fffbeb]/50 p-4 shadow-xs">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="rounded bg-[#b45309] text-white font-mono text-xs font-bold px-2 py-0.5">
                            {item.horaCita?.slice(0, 5)}
                          </span>
                          <h3 className="font-bold text-on-surface">{item.pacienteNombre}</h3>
                        </div>
                        <p className="text-xs text-on-surface-variant mt-1">
                          {[item.doctorNombre, item.motivoConsulta, item.pacienteTelefono ? `Cel: ${item.pacienteTelefono}` : null].filter(Boolean).join(' · ')}
                        </p>
                      </div>

                      <div className="flex items-center gap-2">
                        {item.enlaceWhatsapp && (
                          <a
                            href={item.enlaceWhatsapp}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 rounded-full bg-[#16a34a] px-3.5 py-1.5 text-xs font-bold text-white hover:bg-[#15803d] active:scale-[0.98] transition-all shadow-sm"
                            title="Enviar WhatsApp de confirmación"
                          >
                            <MessageCircle className="size-3.5" />
                            WhatsApp
                          </a>
                        )}
                        {item.pacienteTelefono && (
                          <a
                            href={`tel:+51${item.pacienteTelefono.replace(/\D/g, '')}`}
                            className="inline-flex items-center gap-1.5 rounded-full bg-[#b45309] px-3.5 py-1.5 text-xs font-bold text-white hover:bg-[#92400e] active:scale-[0.98] transition-all shadow-sm"
                          >
                            <Phone className="size-3.5" />
                            Llamar ({item.pacienteTelefono})
                          </a>
                        )}
                      </div>
                    </div>

                    {item.citaId && (
                      <div className="flex flex-wrap items-center gap-1.5 border-t border-amber-200/60 pt-2.5">
                        <span className="text-xs font-semibold text-on-surface-variant mr-1">Registrar llamada:</span>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'LLAMADA', 'CONFIRMO')}
                          className="rounded-full bg-[#ecfdf5] hover:bg-[#d1fae5] text-[#065f46] border border-[#a7f3d0] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          ✓ Confirmó
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'LLAMADA', 'PIDIO_REPROGRAMAR')}
                          className="rounded-full bg-[#fffbeb] hover:bg-[#fef3c7] text-[#92400e] border border-[#fde68a] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          Pidió reprogramar
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'LLAMADA', 'CANCELO')}
                          className="rounded-full bg-[#fef2f2] hover:bg-[#fee2e2] text-[#991b1b] border border-[#fecaca] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          Canceló
                        </button>
                        <button
                          type="button"
                          onClick={() => manejarResultado(item.citaId!, 'LLAMADA', 'NO_RESPONDE')}
                          className="rounded-full bg-[#f1f5f9] hover:bg-[#e2e8f0] text-[#475569] border border-[#cbd5e1] px-2.5 py-1 text-xs font-bold transition-colors cursor-pointer"
                        >
                          No respondió
                        </button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </Seccion>
          )}

          {/* Sección 4: Adelantos por validar (Yape) (solo si hay items pendientes) */}
          {adelantosPorValidar.length > 0 && (
            <Seccion
              id="sec-adelantos"
              titulo="Adelantos por validar"
              icono={<Hourglass />}
              tono="villamax"
              contador={`${adelantosPorValidar.length} por vencer`}
              descripcion="Citas separadas temporalmente pendientes de comprobante Yape o efectivo."
            >
              <ul className="flex flex-col gap-3">
                {adelantosPorValidar.map((item) => (
                  <li key={item.citaId ?? item.id} className="flex flex-wrap items-center justify-between gap-4 rounded-panel border border-outline-variant/60 bg-surface-container-low p-4 shadow-xs">
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="font-bold text-on-surface">{item.pacienteNombre}</h3>
                        {item.pacienteDni && <span className="rounded bg-surface-container px-1.5 py-0.5 text-xs text-on-surface-variant">DNI {item.pacienteDni}</span>}
                      </div>
                      <p className="text-xs text-on-surface-variant">
                        Monto: <strong className="text-primary font-mono">S/ 30.00</strong> · Cita a las {item.horaCita?.slice(0, 5)}
                      </p>
                      {item.minutosParaExpirar != null && (
                        <span className="inline-flex items-center gap-1 text-xs font-bold text-[#b45309] mt-1">
                          <Clock className="size-3" /> Expira en: {item.minutosParaExpirar} min
                        </span>
                      )}
                    </div>

                    {item.citaId && (
                      <Button
                        variant="primario"
                        size="sm"
                        onClick={() => setAdelantoModal({ citaId: item.citaId!, pacienteNombre: item.pacienteNombre, monto: 30 })}
                        className="gap-1.5"
                      >
                        Registrar pago
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </Seccion>
          )}

          {/* Sección 5: Reservas Web Pendientes */}
          <Seccion
            id="sec-web"
            titulo="Reservas web por revisar"
            icono={<Globe />}
            tono="villamax"
            contador={reservasWeb.data ? `${reservasWeb.data.length} reservas web` : undefined}
            descripcion="Reservas hechas por pacientes en la web (pendientes de confirmación). Acéptalas o cancélalas tras contactar al paciente."
          >
            <Consulta
              consulta={reservasWeb}
              cargando="Cargando las reservas web…"
              filas={2}
              vacio={{ titulo: 'No hay reservas web pendientes' }}
            >
              {(citas) => (
                <ul className="flex flex-col gap-3">
                  {citas.map((c) => (
                    <CitaFila
                      key={c.id}
                      cita={c}
                      conFecha
                      onVerDetalle={dialogos.verDetalle}
                      acciones={
                        <AccionesCita
                          cita={c}
                          soloEstados={['PROGRAMADA']}
                          onCancelar={dialogos.cancelar}
                          onReprogramar={dialogos.reprogramar}
                        />
                      }
                    />
                  ))}
                </ul>
              )}
            </Consulta>
          </Seccion>
        </div>

        {/* Sidebar / Columna derecha Stitch B2 */}
        <aside aria-label="Sala de espera" className="flex flex-col gap-6">
          <TarjetaCumpleanos titulo="Cumpleaños de hoy" lista={cumpleanosHoy} mostrarWhatsapp sedeNombre={sede?.nombre} />
          <TarjetaCumpleanos titulo="Cumplen años mañana" lista={cumpleanosManana} mostrarWhatsapp={false} />

          {/* Card Laboratorio Dental (Módulo 6 preparación) */}
          <Card className="gap-3 p-5 border border-outline-variant/60">
            <div className="flex items-center justify-between border-b border-outline-variant/60 pb-2">
              <h3 className="font-display text-sm font-bold text-primary flex items-center gap-1.5">
                <FlaskConical className="size-4" />
                Laboratorio dental
              </h3>
              <span className="rounded-pill bg-surface-container px-2 py-0.5 text-xs font-bold text-on-surface-variant">
                Activo
              </span>
            </div>
            <p className="text-xs text-on-surface-variant">
              Trazabilidad de prótesis y coronas enviadas a laboratorios de prótesis dental.
            </p>
            <div className="rounded-xl bg-surface-container-low p-3 text-xs space-y-1">
              <p className="font-semibold text-on-surface">Trabajos en proceso para esta sede</p>
              <p className="text-xs text-on-surface-variant">No hay entregas atrasadas pendientes de recojo.</p>
            </div>
          </Card>

          {/* Card Sala de Espera */}
          <Seccion
            id="sec-espera"
            titulo="Sala de espera"
            icono={<DoorOpen />}
            tono="villamax"
            contador={listaEspera.data ? `${listaEspera.data.length}` : undefined}
            descripcion="Pacientes que ya llegaron hoy (estado En sala de espera)."
          >
            <Consulta
              consulta={listaEspera}
              cargando="Cargando la sala de espera…"
              filas={2}
              vacio={{ titulo: 'Sala vacía', descripcion: 'Marca la llegada de un paciente para verlo aquí.' }}
            >
              {(citas) => (
                <ul className="flex flex-col gap-3">
                  {citas.map((c) => (
                    <li key={c.id} className="flex flex-col gap-2 rounded-panel bg-surface-container-low p-3">
                      <div className="flex items-baseline justify-between gap-2">
                        <h3 className="font-bold">{c.pacienteNombre || 'Paciente sin nombre'}</h3>
                        <Eyebrow>{c.hora?.slice(0, 5)}</Eyebrow>
                      </div>
                      <p className="text-sm text-on-surface-variant">
                        {[c.doctorNombre, c.sillonNumero != null ? `Sillón ${c.sillonNumero}` : null].filter(Boolean).join(' · ') ||
                          'Sin especialista asignado'}
                      </p>
                      {c.llegadaEn && (
                        <p className="text-xs font-semibold text-on-surface-variant">
                          Espera {minutosEntreInstantes(c.llegadaEn) ?? 0} min
                        </p>
                      )}
                      <AccionesCita cita={c} onCancelar={dialogos.cancelar} />
                    </li>
                  ))}
                </ul>
              )}
            </Consulta>
          </Seccion>
        </aside>
      </div>

      {/* Modal para Registrar Pago de Adelanto Yape */}
      {adelantoModal && (
        <Dialog open={!!adelantoModal} onOpenChange={(abierto) => !abierto && setAdelantoModal(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Registrar Pago de Adelanto Yape / Efectivo</DialogTitle>
              <DialogDescription>
                Confirmación de acreditación para la cita de {adelantoModal.pacienteNombre ?? 'paciente'}.
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={handleConfirmarAdelanto} className="flex flex-col gap-4">
              <div>
                <label htmlFor={`${uid}-medio-de-pago`} className="text-xs font-bold text-on-surface-variant">Medio de Pago</label>
                <select
                  id={`${uid}-medio-de-pago`}
                  value={adelantoForm.medioPago}
                  onChange={(e) => setAdelantoForm({ ...adelantoForm, medioPago: e.target.value })}
                  className="mt-1 w-full rounded-control border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                >
                  <option value="YAPE">Yape (Móvil)</option>
                  <option value="PLIN">Plin</option>
                  <option value="EFECTIVO">Efectivo en recepción</option>
                  <option value="TARJETA_DEBITO">Tarjeta de débito</option>
                  <option value="TARJETA_CREDITO">Tarjeta de crédito</option>
                </select>
              </div>

              <div>
                <label htmlFor={`${uid}-monto-recibido-s`} className="text-xs font-bold text-on-surface-variant">Monto Recibido (S/)</label>
                <Input
                  id={`${uid}-monto-recibido-s`}
                  type="number"
                  step="0.01"
                  value={adelantoForm.monto}
                  onChange={(e) => setAdelantoForm({ ...adelantoForm, monto: e.target.value })}
                  required
                />
              </div>

              <div>
                <label htmlFor={`${uid}-enlace-o-url-de`} className="text-xs font-bold text-on-surface-variant">Enlace o URL de Captura (Opcional)</label>
                <Input
                  id={`${uid}-enlace-o-url-de`}
                  placeholder="https://..."
                  value={adelantoForm.comprobanteUrl}
                  onChange={(e) => setAdelantoForm({ ...adelantoForm, comprobanteUrl: e.target.value })}
                />
              </div>

              <div className="mt-2 flex justify-end gap-2 border-t border-outline-variant/60 pt-4">
                <Button variant="secundario" type="button" onClick={() => setAdelantoModal(null)}>
                  Cancelar
                </Button>
                <Button variant="primario" type="submit" disabled={registrarAdelanto.isPending}>
                  {registrarAdelanto.isPending ? 'Guardando...' : 'Confirmar con adelanto'}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      )}

      <DialogosCita dialogos={dialogos} />
    </div>
  )
}
