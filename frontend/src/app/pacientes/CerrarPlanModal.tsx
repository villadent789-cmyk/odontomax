import { useState } from 'react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { Alert } from '@/design-system/components/Alert'
import type { TratamientoDTO } from '../tratamientos/api-tratamientos'

interface CerrarPlanModalProps {
  abierto: boolean
  alCerrar: () => void
  tratamiento: TratamientoDTO | null
  alConfirmar: (estado: 'TERMINADO' | 'INTERRUMPIDO', motivo?: string) => Promise<void>
}

export function CerrarPlanModal({
  abierto,
  alCerrar,
  tratamiento,
  alConfirmar,
}: CerrarPlanModalProps) {
  const [opcion, setOpcion] = useState<'TERMINADO' | 'INTERRUMPIDO'>('TERMINADO')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (!tratamiento) return null

  async function handleConfirmar() {
    setError(null)
    if (opcion === 'INTERRUMPIDO' && !motivo.trim()) {
      setError('Debe ingresar un motivo para interrumpir el tratamiento')
      return
    }

    setEnviando(true)
    try {
      await alConfirmar(opcion, opcion === 'INTERRUMPIDO' ? motivo.trim() : undefined)
      setMotivo('')
      setOpcion('TERMINADO')
      alCerrar()
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Error al cerrar el plan de tratamiento'
      setError(msg)
    } finally {
      setEnviando(false)
    }
  }

  function handleCancelar() {
    setError(null)
    setMotivo('')
    setOpcion('TERMINADO')
    alCerrar()
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && handleCancelar()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Cerrar plan de tratamiento</DialogTitle>
          <DialogDescription>
            Seleccione el estado final para el plan <strong>{tratamiento.nombre}</strong>. Un plan cerrado no admite nuevas sesiones.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {error && <Alert tono="error" titulo={error} />}

          <div className="space-y-3" role="radiogroup" aria-label="Tipo de cierre">
            <button
              type="button"
              role="radio"
              aria-checked={opcion === 'TERMINADO'}
              onClick={() => {
                setOpcion('TERMINADO')
                setError(null)
              }}
              className={`w-full text-left flex items-start gap-3 p-3 rounded-card border transition-all cursor-pointer ${
                opcion === 'TERMINADO'
                  ? 'border-primary bg-primary/5 ring-1 ring-primary'
                  : 'border-outline-variant/40 hover:bg-surface-container-low'
              }`}
            >
              <div className="flex-1 text-xs">
                <span className="font-bold text-on-surface block text-sm">Terminado</span>
                <span className="text-on-surface-variant">Concluido con éxito (tratamiento finalizado).</span>
              </div>
            </button>

            <button
              type="button"
              role="radio"
              aria-checked={opcion === 'INTERRUMPIDO'}
              onClick={() => {
                setOpcion('INTERRUMPIDO')
                setError(null)
              }}
              className={`w-full text-left flex items-start gap-3 p-3 rounded-card border transition-all cursor-pointer ${
                opcion === 'INTERRUMPIDO'
                  ? 'border-primary bg-primary/5 ring-1 ring-primary'
                  : 'border-outline-variant/40 hover:bg-surface-container-low'
              }`}
            >
              <div className="flex-1 text-xs">
                <span className="font-bold text-on-surface block text-sm">Interrumpido por el paciente</span>
                <span className="text-on-surface-variant">El paciente suspende o abandona el tratamiento antes de completarlo.</span>
              </div>
            </button>
          </div>

          {opcion === 'INTERRUMPIDO' && (
            <Field label="Motivo de la interrupción *">
              <Input
                id="motivo-cierre"
                placeholder="Ej. Paciente se muda de ciudad, motivos económicos..."
                value={motivo}
                onChange={(e) => {
                  setMotivo(e.target.value)
                  if (error) setError(null)
                }}
              />
            </Field>
          )}
        </div>

        <DialogFooter className="flex justify-end gap-2 pt-2">
          <Button variant="secundario" onClick={handleCancelar} disabled={enviando}>
            Cancelar
          </Button>
          <Button variant="primario" onClick={handleConfirmar} disabled={enviando}>
            {enviando ? 'Cerrando...' : 'Confirmar Cierre'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
