import { useId, useState } from 'react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { useRegistrarPagoLaboratorioMutation } from '../api-laboratorio'

interface Props {
  abierto: boolean
  onCerrar: () => void
  ordenId: string
  saldoPendiente: number
}

export function RegistrarPagoLaboratorioModal({ abierto, onCerrar, ordenId, saldoPendiente }: Props) {
  const uid = useId()
  const registrarPago = useRegistrarPagoLaboratorioMutation()
  const [monto, setMonto] = useState(saldoPendiente.toFixed(2))
  const [metodoPago, setMetodoPago] = useState<'EFECTIVO' | 'YAPE' | 'PLIN'>('EFECTIVO')
  const [notas, setNotas] = useState('')
  const [errorValidacion, setErrorValidacion] = useState('')

  const montoNumerico = Number(monto)

  const enviar = async (event: React.FormEvent) => {
    event.preventDefault()
    setErrorValidacion('')
    if (!Number.isFinite(montoNumerico) || montoNumerico <= 0 || montoNumerico > saldoPendiente) {
      setErrorValidacion(`Ingresa un monto mayor a cero y no mayor a S/ ${saldoPendiente.toFixed(2)}.`)
      return
    }

    try {
      await registrarPago.mutateAsync({
        ordenId,
        payload: {
          monto: montoNumerico,
          metodoPago,
          notas: notas.trim() || undefined,
        },
      })
      onCerrar()
    } catch (error: unknown) {
      setErrorValidacion(error instanceof Error ? error.message : 'No se pudo registrar el pago.')
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Registrar pago al laboratorio</DialogTitle>
          <DialogDescription>
            Saldo pendiente: S/ {saldoPendiente.toFixed(2)}
          </DialogDescription>
        </DialogHeader>

        <form noValidate onSubmit={enviar} className="space-y-4">
          <div className="space-y-1">
            <label htmlFor={`${uid}-monto`} className="text-sm font-medium text-slate-700">Monto</label>
            <input
              id={`${uid}-monto`}
              type="number"
              min="0.01"
              max={saldoPendiente}
              step="0.01"
              required
              value={monto}
              onChange={(event) => setMonto(event.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor={`${uid}-metodo`} className="text-sm font-medium text-slate-700">Método de pago</label>
            <select
              id={`${uid}-metodo`}
              value={metodoPago}
              onChange={(event) => setMetodoPago(event.target.value as typeof metodoPago)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            >
              <option value="EFECTIVO">Efectivo</option>
              <option value="YAPE">Yape</option>
              <option value="PLIN">Plin</option>
            </select>
          </div>

          <div className="space-y-1">
            <label htmlFor={`${uid}-notas`} className="text-sm font-medium text-slate-700">Notas (opcional)</label>
            <textarea
              id={`${uid}-notas`}
              value={notas}
              onChange={(event) => setNotas(event.target.value)}
              rows={2}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </div>

          {errorValidacion && (
            <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{errorValidacion}</p>
          )}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="secundario" onClick={onCerrar}>Cancelar</Button>
            <Button type="submit" variant="primario" disabled={registrarPago.isPending}>
              {registrarPago.isPending ? 'Registrando…' : 'Registrar pago'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
