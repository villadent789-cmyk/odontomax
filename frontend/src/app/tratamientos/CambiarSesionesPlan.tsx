import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { Alert, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Field, Input, toast } from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { formatFechaHora } from '@/shared/format'
import { useActualizarTratamientoMutation, type TratamientoDTO } from './api-tratamientos'

/**
 * Cambia el número de sesiones de un plan. El doctor sabe cuántas hacen falta, pero a veces el tiempo no alcanza y el
 * paciente se retira con menos: se puede modificar siempre con un motivo, que queda registrado y a la vista.
 */
export function CambiarSesionesPlan({ plan, puedeEditar = true }: { plan: TratamientoDTO; puedeEditar?: boolean }) {
  const [abierto, setAbierto] = useState(false)
  const [sesiones, setSesiones] = useState(String(plan.totalSesiones ?? 1))
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const actualizar = useActualizarTratamientoMutation()

  const hechas = plan.sesionesRealizadas ?? 0
  const cerrado = plan.estado === 'COMPLETADO' || plan.estado === 'TERMINADO' || plan.estado === 'CANCELADO' || plan.estado === 'INTERRUMPIDO'
  const cambios = plan.cambiosSesiones ?? []

  const guardar = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    const nuevo = Number(sesiones)
    if (!Number.isInteger(nuevo) || nuevo < Math.max(1, hechas)) {
      setError(`El plan debe tener al menos ${Math.max(1, hechas)} sesión(es): las ya realizadas.`)
      return
    }
    if (motivo.trim().length < 5) {
      setError('Indica el motivo del cambio (mínimo 5 caracteres).')
      return
    }
    try {
      await actualizar.mutateAsync({ id: plan.id!, dto: { totalSesiones: nuevo, motivoCambioSesiones: motivo.trim() } })
      toast(`Sesiones de ${plan.nombre}: ahora ${nuevo}`)
      setMotivo('')
      setAbierto(false)
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        {puedeEditar && !cerrado && (
          <button
            type="button"
            onClick={() => {
              setSesiones(String(plan.totalSesiones ?? 1))
              setAbierto(true)
            }}
            className="inline-flex items-center gap-1 rounded-control px-1.5 py-0.5 text-xs font-semibold text-primary hover:bg-surface-container"
          >
            <Pencil aria-hidden className="size-3" />
            Cambiar sesiones
          </button>
        )}
        {cambios.length > 0 && (
          <ul className="flex flex-col gap-0.5 text-[11px] text-on-surface-variant" aria-label="Cambios de sesiones">
            {cambios.map((c) => (
              <li key={`${c.creadoEn}-${c.sesionesDespues}`}>
                {c.sesionesAntes} → {c.sesionesDespues} sesiones · {c.motivo}
                {c.usuarioNombre ? ` · ${c.usuarioNombre}` : ''}
                {c.creadoEn ? ` · ${formatFechaHora(c.creadoEn)}` : ''}
              </li>
            ))}
          </ul>
        )}
      </div>

      <Dialog open={abierto} onOpenChange={setAbierto}>
        <DialogContent className="max-w-md">
          <DialogTitle className="font-display text-lg font-bold text-primary">Cambiar sesiones del plan</DialogTitle>
          <DialogDescription>
            {plan.nombre}: lleva {hechas} de {plan.totalSesiones ?? 1} sesiones. Todo cambio necesita un motivo.
          </DialogDescription>
          <form onSubmit={guardar} noValidate className="flex flex-col gap-3">
            <Field label="Número de sesiones del plan">
              <Input type="number" min={Math.max(1, hechas)} max={60} step={1} inputMode="numeric" value={sesiones} onChange={(e) => setSesiones(e.target.value)} />
            </Field>
            <Field label="Motivo del cambio *">
              <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Tenía otro paciente agendado y se retiró" />
            </Field>
            {error && <Alert tono="error" titulo={error} />}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secundario" onClick={() => setAbierto(false)} disabled={actualizar.isPending}>
                Cancelar
              </Button>
              <Button type="submit" cargando={actualizar.isPending}>
                Guardar cambio
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
