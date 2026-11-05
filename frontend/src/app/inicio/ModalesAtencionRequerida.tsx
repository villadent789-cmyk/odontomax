import { useState } from 'react'
import { Calendar, Check, CheckCircle2, Clock, MapPin, MessageCircle, Phone, Ticket, Trash2 } from 'lucide-react'
import {
  Alert,
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  toast,
} from '@/design-system'
import { formatFecha } from '@/shared/format'
import {
  useBandejaTareas,
  useCambiarEstado,
  useCancelarCita,
  useRegistrarAdelanto,
  useRegistrarContacto,
  useReservasWebPendientes,
  type ItemTareaBandeja,
  type Cita,
} from '../agenda/api-agenda'
import { horaCorta } from '../agenda/fechas'

/* ───────── 1. MODAL RESERVAS WEB PENDIENTES ───────── */

/** Monto sugerido del adelanto por Yape cuando la tarea no trae uno propio. */
const MONTO_ADELANTO_POR_DEFECTO = 30

export function ModalReservasWeb({
  abierto,
  onCerrar,
  sedeId,
}: {
  abierto: boolean
  onCerrar: () => void
  sedeId: string | null
}) {
  const reservasQ = useReservasWebPendientes(sedeId)
  const cambiarEstado = useCambiarEstado()
  const cancelarCita = useCancelarCita()
  const [procesandoId, setProcesandoId] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const reservas = reservasQ.data ?? []

  const handleConfirmar = async (reserva: Cita) => {
    try {
      setProcesandoId(reserva.id!)
      setErrorMsg(null)
      await cambiarEstado.mutateAsync({ id: reserva.id!, estado: 'CONFIRMADA' })
      toast('Reserva confirmada con éxito', {
        description: `${reserva.pacienteNombre ?? 'Paciente'} · ${reserva.fecha ? formatFecha(reserva.fecha) : ''} ${horaCorta(reserva.hora)}`,
      })
    } catch (err: unknown) {
      setErrorMsg((err as { message?: string })?.message ?? 'No se pudo confirmar la reserva.')
    } finally {
      setProcesandoId(null)
    }
  }

  const handleRechazar = async (reserva: Cita) => {
    try {
      setProcesandoId(reserva.id!)
      setErrorMsg(null)
      await cancelarCita.mutateAsync({ id: reserva.id!, motivo: 'Rechazada por recepción desde Inicio' })
      toast('Reserva rechazada', {
        description: `La cita de ${reserva.pacienteNombre ?? 'Paciente'} fue cancelada.`,
      })
    } catch (err: unknown) {
      setErrorMsg((err as { message?: string })?.message ?? 'No se pudo rechazar la reserva.')
    } finally {
      setProcesandoId(null)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col p-6">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2 text-xl font-display font-bold text-primary">
            <Ticket aria-hidden className="size-5 text-tertiary" />
            Reservas Web Pendientes de Confirmación
          </DialogTitle>
          <DialogDescription className="text-on-surface-variant text-sm">
            Pacientes que solicitaron cita por la web y esperan confirmación de recepción.
          </DialogDescription>
        </DialogHeader>

        {errorMsg && (
          <Alert tono="error" titulo="Error">
            {errorMsg}
          </Alert>
        )}

        <div className="flex-1 overflow-y-auto space-y-3 py-2 pr-1">
          {reservasQ.isLoading ? (
            <p className="text-center py-8 text-on-surface-variant">Cargando reservas web…</p>
          ) : reservas.length === 0 ? (
            <div className="rounded-xl border border-dashed border-outline-variant/60 p-8 text-center bg-surface-container-low/50">
              <CheckCircle2 aria-hidden className="size-10 text-emerald-600 mx-auto mb-2 opacity-80" />
              <p className="font-bold text-on-surface">No hay reservas web pendientes</p>
              <p className="text-xs text-on-surface-variant mt-1">Todas las solicitudes online han sido gestionadas.</p>
            </div>
          ) : (
            reservas.map((r) => (
              <div
                key={r.id}
                className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-xs hover:border-outline-variant/80 transition-all"
              >
                <div className="space-y-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-bold text-on-surface text-base">{r.pacienteNombre}</span>
                    {r.codigoTicket && (
                      <Badge tono="neutro" className="font-mono text-xs">
                        {r.codigoTicket}
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm font-semibold text-primary">{r.motivoConsulta}</p>
                  <div className="flex flex-wrap items-center gap-3 text-xs text-on-surface-variant">
                    <span className="flex items-center gap-1">
                      <Calendar aria-hidden className="size-3.5" />
                      {r.fecha ? formatFecha(r.fecha) : ''}
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock aria-hidden className="size-3.5" />
                      {horaCorta(r.hora)} ({r.duracionMinutos ?? 30} min)
                    </span>
                    {r.pacienteTelefono && (
                      <span className="flex items-center gap-1">
                        <Phone aria-hidden className="size-3.5" />
                        {r.pacienteTelefono}
                      </span>
                    )}
                    {r.sedeId && (
                      <span className="flex items-center gap-1">
                        <MapPin aria-hidden className="size-3.5" />
                        {r.sedeId}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 pt-2 sm:pt-0">
                  <Button
                    size="sm"
                    variant="peligro"
                    disabled={procesandoId === r.id}
                    onClick={() => handleRechazar(r)}
                    aria-label={`Rechazar reserva de ${r.pacienteNombre}`}
                  >
                    <Trash2 aria-hidden className="size-3.5 sm:mr-1" />
                    <span className="hidden sm:inline">Rechazar</span>
                  </Button>
                  <Button
                    size="sm"
                    variant="primario"
                    disabled={procesandoId === r.id}
                    onClick={() => handleConfirmar(r)}
                    aria-label={`Confirmar reserva de ${r.pacienteNombre}`}
                  >
                    <Check aria-hidden className="size-3.5 sm:mr-1" />
                    <span>Confirmar</span>
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>

        <DialogFooter className="pt-2 border-t border-outline-variant/30">
          <Button variant="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ───────── 2. MODAL VALIDAR ADELANTOS YAPE ───────── */

export function ModalValidarAdelantos({
  abierto,
  onCerrar,
  sedeId,
  hoy,
}: {
  abierto: boolean
  onCerrar: () => void
  sedeId: string | null
  hoy: string
}) {
  const tareasQ = useBandejaTareas(sedeId, hoy)
  const registrarAdelanto = useRegistrarAdelanto()
  const [procesandoId, setProcesandoId] = useState<string | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const tareas = (tareasQ.data?.tareas ?? []).filter((t) => t.tipo === 'ADELANTO_POR_EXPIRAR')

  const handleValidar = async (t: ItemTareaBandeja) => {
    try {
      setProcesandoId(t.citaId ?? null)
      setErrorMsg(null)
      await registrarAdelanto.mutateAsync({
        id: t.citaId ?? '',
        cuerpo: {
          medioPago: 'YAPE',
          monto: MONTO_ADELANTO_POR_DEFECTO,
        },
      })
      toast('Adelanto Yape validado', {
        description: `Se confirmó el depósito de S/ ${MONTO_ADELANTO_POR_DEFECTO.toFixed(2)} para ${t.pacienteNombre ?? 'Paciente'}.`,
      })
    } catch (err: unknown) {
      setErrorMsg((err as { message?: string })?.message ?? 'No se pudo validar el adelanto.')
    } finally {
      setProcesandoId(null)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-xl max-h-[85vh] flex flex-col p-6">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2 text-xl font-display font-bold text-primary">
            <CheckCircle2 aria-hidden className="size-5 text-emerald-600" />
            Adelantos Yape por Validar
          </DialogTitle>
          <DialogDescription className="text-on-surface-variant text-sm">
            Verifica el depósito y confirma el adelanto de las citas para evitar que expiren.
          </DialogDescription>
        </DialogHeader>

        {errorMsg && (
          <Alert tono="error" titulo="Error">
            {errorMsg}
          </Alert>
        )}

        <div className="flex-1 overflow-y-auto space-y-3 py-2 pr-1">
          {tareasQ.isLoading ? (
            <p className="text-center py-8 text-on-surface-variant">Cargando tareas…</p>
          ) : tareas.length === 0 ? (
            <div className="rounded-xl border border-dashed border-outline-variant/60 p-8 text-center bg-surface-container-low/50">
              <CheckCircle2 aria-hidden className="size-10 text-emerald-600 mx-auto mb-2 opacity-80" />
              <p className="font-bold text-on-surface">No hay adelantos pendientes por validar</p>
              <p className="text-xs text-on-surface-variant mt-1">Todos los pagos Yape están al día.</p>
            </div>
          ) : (
            tareas.map((t) => (
              <div
                key={t.citaId}
                className="p-4 rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-xs space-y-3"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold text-on-surface">{t.pacienteNombre}</p>
                    <p className="text-xs text-on-surface-variant">
                      Cita: {horaCorta(t.horaCita)}{t.minutosParaExpirar != null ? ` · vence en ${t.minutosParaExpirar} min` : ''}
                    </p>
                  </div>
                  <Badge tono="accion" className="font-bold">
                    S/ {MONTO_ADELANTO_POR_DEFECTO.toFixed(2)}
                  </Badge>
                </div>

                <div className="flex items-center gap-2">
                  <p className="flex-1 text-xs text-on-surface-variant">Confirma que el Yape llegó a la cuenta.</p>
                  <Button
                    size="sm"
                    variant="primario"
                    disabled={procesandoId === t.citaId}
                    onClick={() => handleValidar(t)}
                  >
                    <Check aria-hidden className="size-3.5 mr-1" />
                    Validar
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>

        <DialogFooter className="pt-2 border-t border-outline-variant/30">
          <Button variant="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* ───────── 3. MODAL RECORDATORIOS DE MAÑANA ───────── */

export function ModalRecordatorios({
  abierto,
  onCerrar,
  sedeId,
  hoy,
}: {
  abierto: boolean
  onCerrar: () => void
  sedeId: string | null
  hoy: string
}) {
  const tareasQ = useBandejaTareas(sedeId, hoy)
  const registrarContacto = useRegistrarContacto()
  const [procesandoId, setProcesandoId] = useState<string | null>(null)

  const tareas = (tareasQ.data?.tareas ?? []).filter((t) => t.tipo === 'RECORDATORIO_24H')

  const handleConfirmarContacto = async (t: ItemTareaBandeja) => {
    try {
      setProcesandoId(t.citaId ?? null)
      await registrarContacto.mutateAsync({
        id: t.citaId ?? '',
        cuerpo: {
          medio: 'WHATSAPP',
          resultado: 'CONFIRMO',
          notas: 'Confirmado por WhatsApp desde Inicio',
        },
      })
      toast('Contacto registrado', {
        description: `Se registró confirmación para ${t.pacienteNombre ?? 'Paciente'}.`,
      })
    } catch {
      toast('Error', { description: 'No se pudo registrar el contacto.' })
    } finally {
      setProcesandoId(null)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-xl max-h-[85vh] flex flex-col p-6">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2 text-xl font-display font-bold text-primary">
            <MessageCircle aria-hidden className="size-5 text-emerald-600" />
            Recordatorios de Citas de Mañana
          </DialogTitle>
          <DialogDescription className="text-on-surface-variant text-sm">
            Comunícate con los pacientes agendados para mañana y confirma su asistencia.
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto space-y-3 py-2 pr-1">
          {tareasQ.isLoading ? (
            <p className="text-center py-8 text-on-surface-variant">Cargando recordatorios…</p>
          ) : tareas.length === 0 ? (
            <div className="rounded-xl border border-dashed border-outline-variant/60 p-8 text-center bg-surface-container-low/50">
              <CheckCircle2 aria-hidden className="size-10 text-emerald-600 mx-auto mb-2 opacity-80" />
              <p className="font-bold text-on-surface">No hay recordatorios pendientes</p>
              <p className="text-xs text-on-surface-variant mt-1">Todas las citas de mañana están confirmadas.</p>
            </div>
          ) : (
            tareas.map((t) => {
              const tel = t.pacienteTelefono?.replace(/\D/g, '')
              const mensajeWsp = encodeURIComponent(
                `Hola ${t.pacienteNombre ?? ''}, le escribimos de la Clínica Dental Villadent para recordarle su cita programada para el día de mañana a las ${horaCorta(t.horaCita)}. ¿Nos confirma su asistencia? ¡Gracias!`
              )
              const linkWsp = tel ? `https://wa.me/51${tel}?text=${mensajeWsp}` : null

              return (
                <div
                  key={t.citaId}
                  className="p-4 rounded-xl border border-outline-variant/40 bg-surface-container-lowest shadow-xs flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <p className="font-bold text-on-surface truncate">
                      {t.pacienteNombre}
                      {t.estadoConfirmacion === 'MENSAJE_ENVIADO' && <span className="ml-2 rounded bg-emerald-100 px-1.5 py-0.5 text-xs font-bold text-emerald-800">Mensaje enviado</span>}
                    </p>
                    <p className="text-xs text-on-surface-variant">
                      Hora: {horaCorta(t.horaCita)} · Tel: {t.pacienteTelefono || 'Sin teléfono'}
                    </p>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    {linkWsp && (
                      <a
                        href={linkWsp}
                        target="_blank"
                        rel="noreferrer"
                        onClick={() => {
                          if (t.citaId && t.estadoConfirmacion !== 'MENSAJE_ENVIADO') {
                            void registrarContacto.mutateAsync({ id: t.citaId, cuerpo: { medio: 'WHATSAPP', resultado: 'MENSAJE_ENVIADO', notas: 'Mensaje abierto en WhatsApp desde Inicio' } })
                          }
                        }}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-600/40 bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700 hover:bg-emerald-100 transition-colors"
                      >
                        <MessageCircle aria-hidden className="size-3.5" />
                        WhatsApp
                      </a>
                    )}
                    <Button
                      size="sm"
                      variant="primario"
                      disabled={procesandoId === t.citaId}
                      onClick={() => handleConfirmarContacto(t)}
                    >
                      <Check aria-hidden className="size-3.5 mr-1" />
                      Confirmado
                    </Button>
                  </div>
                </div>
              )
            })
          )}
        </div>

        <DialogFooter className="pt-2 border-t border-outline-variant/30">
          <Button variant="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
