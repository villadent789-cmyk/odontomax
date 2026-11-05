import { useState } from "react"
import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  Input,
  Label,
  toast,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { cn } from '@/design-system/cn'
import { useSesion } from '@/shared/auth'
import { formatFecha, hoyEnLima } from '@/shared/format'
import { HttpError } from '@/shared/http'
import { useReprogramarCita, type Cita } from './api-agenda'
import { mensajeConflicto } from './errores'
import { esquemaReprogramar, type DatosReprogramar } from './esquemas'
import { horaCorta } from './fechas'
import { SelectorHueco } from './SelectorHueco'

const MOTIVOS_REPROGRAMACION = [
  'Solicitud del paciente por cruce de horario',
  'Especialista ausente / cambio de turno',
  'Requerimiento de preparación previa / exámenes',
  'Fuerza mayor / mantenimiento en sede',
  'Otro',
] as const

interface Props {
  cita: Cita | null
  onCerrar: () => void
  onReprogramada?: (cita: Cita) => void
}

/**
 * B5 · Reprogramar (PATCH /citas/{id}/reprogramar { nuevaFecha, nuevoHorario, motivo }). Mantiene especialista,
 * sillón y duración (el backend no permite cambiarlos al reprogramar). Si la cita tiene especialista, el horario se
 * elige entre sus horarios libres; si no, se escribe la hora.
 */
export function ReprogramarDialog({ cita, onCerrar, onReprogramada }: Props) {
  return (
    <Dialog open={!!cita} onOpenChange={(abierto) => !abierto && onCerrar()}>
      {cita && <Formulario key={cita.id} cita={cita} onCerrar={onCerrar} onReprogramada={onReprogramada} />}
    </Dialog>
  )
}

function Formulario({ cita, onCerrar, onReprogramada }: { cita: Cita } & Omit<Props, 'cita'>) {
  const sesion = useSesion()
  const qc = useQueryClient()
  const reprogramar = useReprogramarCita()
  const hoy = hoyEnLima()
  const actual = horaCorta(cita.hora)
  const [motivoTipo, setMotivoTipo] = useState<string>('')

  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    formState: { errors },
  } = useForm<DatosReprogramar>({
    resolver: zodResolver(esquemaReprogramar),
    defaultValues: { nuevaFecha: cita.fecha && cita.fecha >= hoy ? cita.fecha : hoy, nuevoHorario: '', motivo: '' },
  })
  const [nuevaFecha, nuevoHorario] = useWatch({ control, name: ['nuevaFecha', 'nuevoHorario'] })

  const enviar = handleSubmit(async (d) => {
    if (d.nuevaFecha === cita.fecha && d.nuevoHorario === actual) {
      setError('nuevoHorario', { message: 'Elige una fecha u hora distinta de la actual.' })
      return
    }
    try {
      const r = await reprogramar.mutateAsync({ id: cita.id!, ...d })
      toast('Cita reprogramada', { description: `${r.pacienteNombre ?? 'Paciente'} · ${formatFecha(r.fecha)} ${horaCorta(r.hora)}` })
      onReprogramada?.(r)
      onCerrar()
    } catch (e) {
      if (e instanceof HttpError && e.status === 409) {
        setValue('nuevoHorario', '')
        void qc.invalidateQueries({ queryKey: ['disponibilidad'] })
      }
    }
  })

  const fechaValida = /^\d{4}-\d{2}-\d{2}$/.test(nuevaFecha) && nuevaFecha >= hoy

  return (
    <DialogContent className="max-w-xl">
      <DialogHeader>
        <DialogTitle>Reprogramar cita</DialogTitle>
        <DialogDescription>
          {cita.pacienteNombre ?? 'Paciente'} · hoy está el {formatFecha(cita.fecha)} a las {actual}
          {cita.doctorNombre ? ` con ${cita.doctorNombre}` : ''}.
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={enviar} noValidate className="flex flex-col gap-5">
        {reprogramar.isError && (
          <Alert tono="error" titulo="No se pudo reprogramar">
            {mensajeConflicto(reprogramar.error)}
          </Alert>
        )}
        <Field label="Nueva fecha" error={errors.nuevaFecha?.message}>
          <Input type="date" min={hoy} {...register('nuevaFecha', { onChange: () => setValue('nuevoHorario', '') })} />
        </Field>
        {cita.doctorId ? (
          <div className="flex flex-col gap-2">
            <Label asChild>
              <p>Nuevo horario</p>
            </Label>
            {fechaValida ? (
              <SelectorHueco
                especialistaId={cita.doctorId}
                fecha={nuevaFecha}
                sedeId={cita.sedeId ?? sesion?.sedeActivaId ?? null}
                duracion={cita.duracionMinutos || 30}
                valor={nuevoHorario}
                onElegir={(h) => setValue('nuevoHorario', h, { shouldValidate: true })}
                propia={cita}
                error={errors.nuevoHorario?.message}
                idError="error-horario"
              />
            ) : (
              <p className="text-sm text-on-surface-variant">Elige una fecha válida para ver los horarios libres.</p>
            )}
            {errors.nuevoHorario && (
              <p id="error-horario" className="text-sm font-bold text-error">
                {errors.nuevoHorario.message}
              </p>
            )}
          </div>
        ) : (
          <Field label="Nuevo horario" error={errors.nuevoHorario?.message} ayuda="La cita no tiene especialista: escribe la hora.">
            <Input type="time" step={1800} {...register('nuevoHorario')} />
          </Field>
        )}
        <Field label="Seleccionar motivo estándar" ayuda="Elige un motivo frecuente o selecciona 'Otro'.">
          <select
            className={cn(inputClases, 'h-10 py-1.5')}
            value={motivoTipo}
            onChange={(e) => {
              const val = e.target.value
              setMotivoTipo(val)
              if (val && val !== 'Otro') {
                setValue('motivo', val, { shouldValidate: true })
              } else if (val === 'Otro') {
                setValue('motivo', '', { shouldValidate: false })
              }
            }}
          >
            <option value="">-- Selecciona un motivo --</option>
            {MOTIVOS_REPROGRAMACION.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Motivo de la reprogramación" error={errors.motivo?.message} ayuda="Mínimo 5 caracteres (obligatorio si eliges 'Otro' o para detalle adicional).">
          <textarea rows={2} className={cn(inputClases, 'h-auto py-3')} {...register('motivo')} />
        </Field>
        <DialogFooter>
          <Button variant="secundario" onClick={onCerrar}>
            Volver
          </Button>
          <Button type="submit" variant="accion" cargando={reprogramar.isPending}>
            Reprogramar
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  )
}
