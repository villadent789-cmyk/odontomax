import { METODOS_PAGO, type MetodoPago } from '@/shared/metodosPago'
import { useState } from 'react'
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { Alert } from '@/design-system/components/Alert'
import { useRegistrarMovimientoMutation, type CajaMovimientoDTO } from './api-caja'
import { textoDeError } from '@/shared/http'

interface Props {
  abierto: boolean
  alCerrar: () => void
  sesionId: string
  alGuardar?: (mov: CajaMovimientoDTO) => void
}

export function RegistrarMovimientoModal({ abierto, alCerrar, sesionId, alGuardar }: Props) {
  const [concepto, setConcepto] = useState('')
  const [monto, setMonto] = useState('')
  const [metodoPago, setMetodoPago] = useState<MetodoPago>('EFECTIVO')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const movimientoMutation = useRegistrarMovimientoMutation()

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (!concepto.trim()) {
      setErrorLocal('El concepto del movimiento es obligatorio')
      return
    }

    const montoNum = parseFloat(monto)
    if (isNaN(montoNum) || montoNum <= 0) {
      setErrorLocal('El monto debe ser mayor a cero')
      return
    }

    if (montoNum > 200) {
      setErrorLocal('Tope de seguridad: El monto de un egreso manual no puede exceder S/ 200.00')
      return
    }

    movimientoMutation.mutate(
      {
        sesionId,
        dto: {
          tipo: 'EGRESO',
          concepto: concepto.trim(),
          monto: montoNum,
          metodoPago,
        },
      },
      {
        onSuccess: (mov) => {
          alGuardar?.(mov)
          setConcepto('')
          setMonto('')
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al registrar el movimiento'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Registrar Movimiento de Caja</DialogTitle>
          <DialogDescription>
            Registra salidas de dinero y el medio de pago utilizado. Se auditará con tu usuario.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && (
          <Alert tono="error" titulo={errorLocal} />
        )}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-2">
          <Field label="Concepto / Motivo *" ayuda="Ej. Compra de agua para sala de espera, artículos de limpieza">
            <Input
              id="conceptoMovimiento"
              value={concepto}
              onChange={(e) => setConcepto(e.target.value)}
              placeholder="Describa el motivo del movimiento"
              required
              ref={enfocarAlMontar}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field
              label="Monto (S/) *"
              ayuda="Máx. S/ 200.00 por movimiento"
            >
              <Input
                id="montoMovimiento"
                type="number"
                step="0.01"
                min="0.01"
                value={monto}
                onChange={(e) => setMonto(e.target.value)}
                placeholder="0.00"
                required
              />
            </Field>

            <Field label="Medio de Pago">
              <select
                id="metodoPagoMovimiento"
                value={metodoPago}
                onChange={(e) => setMetodoPago(e.target.value as MetodoPago)}
                className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {METODOS_PAGO.map((m) => (<option key={m.valor} value={m.valor}>{m.nombre}</option>))}
              </select>
            </Field>
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={movimientoMutation.isPending}>
              Cancelar
            </Button>
            <Button
              type="submit"
              variant="peligro"
              disabled={movimientoMutation.isPending}
            >
              {movimientoMutation.isPending ? 'Registrando...' : 'Registrar Egreso'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
