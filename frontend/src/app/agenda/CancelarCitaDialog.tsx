import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { MessageCircle, Sparkles, CheckCircle2 } from 'lucide-react'
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
  toast,
  buttonVariants,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { cn } from '@/design-system/cn'
import { formatFecha } from '@/shared/format'
import { useCancelarCita, useListaEspera, type Cita } from './api-agenda'
import { mensajeError } from './errores'
import { horaCorta } from './fechas'

const MOTIVOS_CANCELACION = [
  'Paciente no puede asistir / cruce de horarios',
  'Emergencia de salud o médica del paciente',
  'Problema personal o permiso del especialista',
  'Paciente desistió del tratamiento',
  'Error involuntario en agendamiento',
  'Otro',
] as const

/** Mismas reglas que Cita.cancelar(): motivo de 5 a 500 caracteres (CitaCancelarRequestDTO: máx. 500). */
const esquema = z.object({
  motivoTipo: z.string().optional(),
  motivo: z
    .string()
    .trim()
    .min(5, 'Indica el motivo de la cancelación (mínimo 5 caracteres).')
    .max(500, 'El motivo no puede exceder los 500 caracteres.'),
})

interface Props {
  cita: Cita | null
  onCerrar: () => void
  onCancelada?: (cita: Cita) => void
}

export function CancelarCitaDialog({ cita, onCerrar, onCancelada }: Props) {
  return (
    <Dialog open={!!cita} onOpenChange={(abierto) => !abierto && onCerrar()}>
      {cita && <Contenido key={cita.id} cita={cita} onCerrar={onCerrar} onCancelada={onCancelada} />}
    </Dialog>
  )
}

function Contenido({ cita, onCerrar, onCancelada }: { cita: Cita } & Omit<Props, 'cita'>) {
  const cancelar = useCancelarCita()
  const { data: listaEspera = [] } = useListaEspera(cita.sedeId ?? null)
  const [canceladaExitosa, setCanceladaExitosa] = useState(false)

  const [motivoTipo, setMotivoTipo] = useState<string>('')
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<z.infer<typeof esquema>>({ resolver: zodResolver(esquema), defaultValues: { motivo: '', motivoTipo: '' } })

  const enviar = handleSubmit(async ({ motivo }) => {
    try {
      const r = await cancelar.mutateAsync({ id: cita.id!, motivo })
      toast('Cita cancelada', { description: `${cita.pacienteNombre ?? 'Paciente'} · ${formatFecha(cita.fecha)}` })
      onCancelada?.(r)
      setCanceladaExitosa(true)
    } catch {
      /* el error se muestra en el formulario */
    }
  })

  const mensajeHuecoLibre = encodeURIComponent(
    `🦷 *VILLADENT ODONTOLOGÍA - CUPO LIBRE DISPONIBLE*\n` +
    `Estimado(a) paciente, le informamos que se ha liberado un cupo de atención odontológica para el *${formatFecha(cita.fecha)} a las ${horaCorta(cita.hora)}*${cita.doctorNombre ? ` con el ${cita.doctorNombre}` : ''}.\n\n` +
    `Si desea tomar este turno prioritario, por favor responda a este mensaje con la palabra *ACEPTAR* para reservarlo de inmediato.\n¡Muchas gracias!`
  )

  if (canceladaExitosa) {
    return (
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2 text-emerald-600">
            <CheckCircle2 className="size-6" />
            <DialogTitle>Horario Liberado en Sillón</DialogTitle>
          </div>
          <DialogDescription>
            El turno del <strong>{formatFecha(cita.fecha)}</strong> a las <strong>{horaCorta(cita.hora)}</strong> ha quedado libre.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3 py-2">
          <div className="rounded-xl border border-teal-200 bg-teal-50/70 p-3.5 dark:border-teal-900/50 dark:bg-teal-950/30">
            <div className="flex items-start gap-2.5">
              <Sparkles className="size-5 shrink-0 text-teal-600 dark:text-teal-400 mt-0.5" />
              <div className="text-xs text-teal-950 dark:text-teal-100">
                <p className="font-bold">Reactivación Inteligente de Hueco Libre</p>
                <p className="mt-0.5 text-teal-800 dark:text-teal-300">
                  {listaEspera.length > 0
                    ? `Hay ${listaEspera.length} paciente(s) en lista de espera para esta sede. Puedes ofrecerles el cupo directamente por WhatsApp.`
                    : 'Puedes difundir o enviar este cupo libre por WhatsApp a pacientes interesados en atención rápida.'}
                </p>
              </div>
            </div>

            {listaEspera.length > 0 && (
              <div className="mt-3 flex flex-col gap-2 border-t border-teal-200/60 pt-2.5 dark:border-teal-900/40">
                {listaEspera.slice(0, 3).map((item) => (
                  <div key={item.id} className="flex items-center justify-between gap-2 text-xs bg-white/80 dark:bg-zinc-900/80 p-2 rounded-lg">
                    <div className="min-w-0">
                      <p className="font-bold text-on-surface truncate">{item.pacienteNombre}</p>
                      <p className="text-xs text-on-surface-variant truncate">{item.motivoConsulta || 'Consulta general'}</p>
                    </div>
                    {item.pacienteTelefono && (
                      <a
                        href={`https://wa.me/51${item.pacienteTelefono.replace(/\D/g, '').slice(-9)}?text=${mensajeHuecoLibre}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={buttonVariants({
                          variant: 'secundario',
                          size: 'sm',
                          className: 'text-emerald-700 dark:text-emerald-300 py-1 px-2.5 text-xs gap-1',
                        })}
                      >
                        <MessageCircle className="size-3.5" />
                        Ofrecer
                      </a>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="flex flex-col gap-2 pt-1">
            <a
              href={`https://wa.me/?text=${mensajeHuecoLibre}`}
              target="_blank"
              rel="noopener noreferrer"
              className={buttonVariants({
                variant: 'primario',
                className: 'w-full gap-2 text-xs py-2 bg-emerald-600 hover:bg-emerald-700 text-white',
              })}
            >
              <MessageCircle className="size-4" />
              Compartir Hueco Libre por WhatsApp
            </a>
          </div>
        </div>

        <DialogFooter>
          <Button variant="secundario" onClick={onCerrar} className="w-full">
            Cerrar
          </Button>
        </DialogFooter>
      </DialogContent>
    )
  }

  return (
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Cancelar cita</DialogTitle>
        <DialogDescription>
          {cita.pacienteNombre ?? 'Paciente sin nombre'} · {formatFecha(cita.fecha)} a las {horaCorta(cita.hora)}. Se
          libera el horario y queda registrado el motivo.
        </DialogDescription>
      </DialogHeader>

      <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-2.5 text-xs text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/20 dark:text-amber-200 flex items-center gap-2">
        <Sparkles className="size-4 shrink-0 text-amber-600" />
        <span>Al cancelar, el sistema te sugerirá ofrecer el cupo libre a pacientes en lista de espera por WhatsApp.</span>
      </div>

      <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
        {cancelar.isError && (
          <Alert tono="error" titulo="No se pudo cancelar la cita">
            {mensajeError(cancelar.error)}
          </Alert>
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
            {MOTIVOS_CANCELACION.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Motivo de la cancelación" error={errors.motivo?.message} ayuda="Entre 5 y 500 caracteres (obligatorio si eliges 'Otro' o para detalle adicional).">
          <textarea rows={3} className={cn(inputClases, 'h-auto py-3')} {...register('motivo')} />
        </Field>
        <DialogFooter>
          <Button variant="secundario" onClick={onCerrar}>
            Volver
          </Button>
          <Button type="submit" variant="peligro" cargando={cancelar.isPending}>
            Cancelar cita
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  )
}
