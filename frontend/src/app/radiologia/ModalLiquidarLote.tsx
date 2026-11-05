import { useState } from 'react'
import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Field } from '@/design-system'
import { formatSoles } from '@/shared/format'
import { api, textoDeError } from '@/shared/http'
import type { ComisionRadiologicaDTO } from './types'

interface Props {
  abierto: boolean
  comisiones: ComisionRadiologicaDTO[]
  onCerrar: () => void
  onLiquidado: () => Promise<void> | void
}

/** Liquida juntas varias comisiones del mismo doctor: un solo pago y un solo egreso de caja por el total. */
export function ModalLiquidarLote({ abierto, comisiones, onCerrar, onLiquidado }: Props) {
  const [metodoPago, setMetodoPago] = useState<'EFECTIVO' | 'YAPE' | 'PLIN'>('YAPE')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const total = comisiones.reduce((suma, c) => suma + c.montoComision, 0)
  const doctores = new Set(comisiones.map((c) => c.doctorDerivadorId))
  const mezcladas = doctores.size > 1

  async function confirmar(evento: React.FormEvent) {
    evento.preventDefault()
    setError(null)
    if (mezcladas) {
      setError('Solo se liquidan juntas las comisiones de un mismo doctor.')
      return
    }
    setEnviando(true)
    try {
      await api.post('/radiologia/comisiones/liquidar-lote', {
        comisionIds: comisiones.map((c) => c.id),
        metodoPago,
      })
      await onLiquidado()
      onCerrar()
    } catch (e) {
      setError(textoDeError(e, 'No se pudieron liquidar las comisiones.'))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(valor) => !valor && onCerrar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Liquidar {comisiones.length} comisiones</DialogTitle>
          <DialogDescription>
            {mezcladas
              ? 'Elegiste comisiones de doctores distintos: deja las de un solo doctor.'
              : `${comisiones[0]?.doctorDerivadorNombre ?? ''} · total ${formatSoles(total)}. Sale de la caja de Villamax en un solo egreso.`}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={confirmar} className="flex flex-col gap-4">
          <Field label="Método de pago *">
            <select
              aria-label="Método de pago"
              value={metodoPago}
              onChange={(e) => setMetodoPago(e.target.value as 'EFECTIVO' | 'YAPE' | 'PLIN')}
              className="w-full rounded-control border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
            >
              <option value="YAPE">Yape</option>
              <option value="PLIN">Plin</option>
              <option value="EFECTIVO">Efectivo</option>
            </select>
          </Field>
          {error && (
            <p role="alert" className="text-sm text-error">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="secundario" onClick={onCerrar}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={enviando || mezcladas}>
              {enviando ? 'Liquidando…' : `Liquidar ${formatSoles(total)}`}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
