import { Armchair, CalendarClock, CircleCheck, CircleX, DoorOpen, Stethoscope, UserX, Zap } from 'lucide-react'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Alert, Button, definicionEstado, toast, type ButtonProps } from '@/design-system'
import { cn } from '@/design-system/cn'
import { useSesion } from '@/shared/auth'
import type { EstadoCita } from '@/shared/api'
import { useCambiarEstado, useIniciarAtencion, type Cita } from './api-agenda'
import { mensajeError } from './errores'
import { ACCION_ESTADO, accionesPara } from './transiciones'
import { FinalizarAtencionModal } from './FinalizarAtencionModal'
import { horaInstanteEnLima, minutosEntreInstantes } from './fechas'

const ICONO: Partial<Record<EstadoCita, ReactNode>> = {
  PROGRAMADA: <CircleCheck aria-hidden />,
  CONFIRMADA: <CircleCheck aria-hidden />,
  EN_SALA: <DoorOpen aria-hidden />,
  ATENDIDA: <Stethoscope aria-hidden />,
  NO_ASISTIO: <UserX aria-hidden />,
}

const VARIANTE: Partial<Record<EstadoCita, ButtonProps['variant']>> = {
  PROGRAMADA: 'primario',
  CONFIRMADA: 'primario',
  EN_SALA: 'primario',
  ATENDIDA: 'primario',
  NO_ASISTIO: 'secundario',
}

interface Props {
  cita: Cita
  onCancelar?: (cita: Cita) => void
  onReprogramar?: (cita: Cita) => void
  /** Tamaño de los botones. */
  tamano?: 'sm' | 'md'
  /** Limita los cambios de estado ofrecidos (p. ej. citas de mañana: solo confirmar). */
  soloEstados?: readonly EstadoCita[]
  /** Recibe la cita tal como la devuelve el backend tras el cambio (p. ej. para refrescar el detalle B4). */
  onActualizada?: (cita: Cita) => void
  className?: string
}

/**
 * Botones de las acciones permitidas para la cita (transiciones de transiciones.ts filtradas por rol).
 * Cambiar estado: PATCH /citas/{id}/estado con { estado } (sin motivo). Cancelar y reprogramar abren su diálogo.
 */
export function AccionesCita({ cita, onCancelar, onReprogramar, tamano = 'sm', soloEstados, onActualizada, className }: Props) {
  const sesion = useSesion()
  const cambiar = useCambiarEstado()
  const iniciar = useIniciarAtencion()
  const todas = accionesPara(cita.estado, sesion?.usuario.rol)
  const acciones = soloEstados ? { ...todas, estados: todas.estados.filter((e) => soloEstados.includes(e)) } : todas
  const paciente = cita.pacienteNombre ?? 'paciente'
  const puedePasarAlSillon = todas.iniciarAtencion && !cita.inicioAtencionEn
  const mostrarFlujoRapido = cita.estado === 'EN_ATENCION' && Boolean(onActualizada)
  const hayAlgo = acciones.estados.length > 0 || (acciones.cancelar && onCancelar) || (acciones.reprogramar && onReprogramar) || puedePasarAlSillon || mostrarFlujoRapido
  const [modalFinalizarAbierto, setModalFinalizarAbierto] = useState(false)
  if (!hayAlgo && !mostrarFlujoRapido) return null

  async function pasarA(estado: EstadoCita) {
    try {
      const actualizada = await cambiar.mutateAsync({ id: cita.id!, estado })
      if (actualizada) onActualizada?.(actualizada)
      toast(`Cita de ${paciente}: ${definicionEstado(estado).texto.toLocaleLowerCase('es-PE')}`)
    } catch {
      /* se muestra abajo */
    }
  }

  async function iniciarAtencion() {
    try {
      const actualizada = await iniciar.mutateAsync({ id: cita.id! })
      if (actualizada) onActualizada?.(actualizada)
      toast(`Atención iniciada para ${paciente}`)
    } catch {
      /* se muestra abajo */
    }
  }

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {cita.estado === 'EN_ATENCION' && cita.inicioAtencionEn && (
        <p className="text-xs font-semibold text-primary">
          En sillón hace {minutosEntreInstantes(cita.inicioAtencionEn) ?? 0} min (desde {horaInstanteEnLima(cita.inicioAtencionEn) ?? 'hora no disponible'})
        </p>
      )}
      {mostrarFlujoRapido && (
        <Button
          size={tamano}
          variant="accion"
          onClick={() => setModalFinalizarAbierto(true)}
          disabled={cambiar.isPending}
          className="w-full"
        >
          <Zap aria-hidden />
          Finalizar Atención y Cobrar
        </Button>
      )}
      <div role="group" aria-label={`Acciones de la cita de ${paciente}`} className="flex flex-wrap gap-2">
        {puedePasarAlSillon && (
          <Button size={tamano} variant="primario" onClick={iniciarAtencion} disabled={iniciar.isPending}>
            <Armchair aria-hidden />
            Pasar al sillón
          </Button>
        )}
        {acciones.estados.map((estado) => (
          <Button
            key={estado}
            size={tamano}
            variant={VARIANTE[estado] ?? 'secundario'}
            cargando={cambiar.isPending && cambiar.variables?.estado === estado}
            disabled={cambiar.isPending}
            onClick={() => pasarA(estado)}
          >
            {ICONO[estado]}
            {ACCION_ESTADO[estado]}
          </Button>
        ))}
        {acciones.reprogramar && onReprogramar && (
          <Button size={tamano} variant="secundario" onClick={() => onReprogramar(cita)} disabled={cambiar.isPending}>
            <CalendarClock aria-hidden />
            Reprogramar
          </Button>
        )}
        {acciones.cancelar && onCancelar && (
          <Button size={tamano} variant="peligro" onClick={() => onCancelar(cita)} disabled={cambiar.isPending}>
            <CircleX aria-hidden />
            Cancelar
          </Button>
        )}
      </div>
      {cambiar.isError && (
        <Alert tono="error" titulo="No se pudo cambiar el estado" onCerrar={() => cambiar.reset()}>
          {mensajeError(cambiar.error)}
        </Alert>
      )}
      {iniciar.isError && (
        <Alert tono="error" titulo="No se pudo iniciar la atención" onCerrar={() => iniciar.reset()}>
          {mensajeError(iniciar.error)}
        </Alert>
      )}
      {mostrarFlujoRapido && onActualizada && (
        <FinalizarAtencionModal
          cita={cita}
          abierto={modalFinalizarAbierto}
          alCerrar={() => setModalFinalizarAbierto(false)}
          onActualizada={onActualizada}
        />
      )}
    </div>
  )
}
