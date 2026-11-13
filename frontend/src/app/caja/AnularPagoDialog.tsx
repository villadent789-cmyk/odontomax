import { useState } from 'react'
import { Alert, Button } from '@/design-system'
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
import { formatSoles } from '@/shared/format'

/** Motivos más comunes: se elige uno y, si hace falta, se agrega un detalle. «Otro motivo» exige el detalle. */
const MOTIVOS_ANULACION = [
  'Error en el monto',
  'Método de pago equivocado (Yape, efectivo, tarjeta)',
  'Cobro duplicado',
  'El paciente no asistió o se retiró',
  'Otro motivo',
] as const

const OTRO = 'Otro motivo'
const MINIMO = 5

interface Props {
  /** Comprobante a anular; con `null` el diálogo está cerrado. */
  pago: { comprobante?: string; monto?: number; metodoPago?: string } | null
  enviando: boolean
  error: string | null
  alCerrar: () => void
  onConfirmar: (motivo: string) => void
}

/** Motivo completo que queda en el pago, en la caja y en Auditoría. */
function motivoCompleto(motivo: string, detalle: string): string {
  const d = detalle.trim()
  return d ? `${motivo}: ${d}` : motivo
}

export function AnularPagoDialog({ pago, enviando, error, alCerrar, onConfirmar }: Props) {
  return (
    <Dialog open={!!pago} onOpenChange={(v) => !v && !enviando && alCerrar()}>
      <DialogContent className="max-w-md">
        {pago && <Formulario pago={pago} enviando={enviando} error={error} alCerrar={alCerrar} onConfirmar={onConfirmar} />}
      </DialogContent>
    </Dialog>
  )
}

function Formulario({ pago, enviando, error, alCerrar, onConfirmar }: Omit<Props, 'pago'> & { pago: NonNullable<Props['pago']> }) {
  const [motivo, setMotivo] = useState<string>(MOTIVOS_ANULACION[0])
  const [detalle, setDetalle] = useState('')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  function confirmar(e: React.FormEvent) {
    e.preventDefault()
    if (motivo === OTRO && detalle.trim().length < MINIMO) {
      setErrorLocal(`Escribe el motivo (al menos ${MINIMO} letras).`)
      return
    }
    setErrorLocal(null)
    onConfirmar(motivoCompleto(motivo, detalle))
  }

  return (
    <form onSubmit={confirmar} className="space-y-4">
      <DialogHeader>
        <DialogTitle>Anular comprobante</DialogTitle>
        <DialogDescription>
          {pago.comprobante ?? 'Comprobante'}
          {pago.monto != null ? ` · ${formatSoles(pago.monto)}` : ''}
          {pago.metodoPago ? ` · ${pago.metodoPago}` : ''}. Queda registrado con tu nombre y se devuelve el saldo al paciente.
        </DialogDescription>
      </DialogHeader>

      {(errorLocal || error) && <Alert tono="error" titulo={errorLocal ?? error ?? ''} />}

      <Field label="Motivo">
        <select
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
        >
          {MOTIVOS_ANULACION.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </Field>
      <Field label={motivo === OTRO ? 'Detalle (obligatorio)' : 'Detalle (opcional)'}>
        <Input value={detalle} onChange={(e) => setDetalle(e.target.value)} maxLength={200} autoComplete="off" placeholder="Ej. Era Yape, no efectivo" />
      </Field>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={enviando}>
          Cancelar
        </Button>
        <Button type="submit" variant="peligro" cargando={enviando} disabled={enviando}>
          Anular comprobante
        </Button>
      </DialogFooter>
    </form>
  )
}
