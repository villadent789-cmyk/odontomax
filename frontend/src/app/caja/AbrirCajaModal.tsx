import { useState } from 'react'
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
import { mensajeError } from '@/shared/errores'
import { useAbrirCajaMutation, type CajaSesionDTO } from './api-caja'

interface Props {
  abierto: boolean
  alCerrar: () => void
  sedeId: string
  sedeNombre?: string
  alGuardar?: (caja: CajaSesionDTO) => void
}

export function AbrirCajaModal({ abierto, alCerrar, sedeId, sedeNombre, alGuardar }: Props) {
  const [observaciones, setObservaciones] = useState('')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)
  const [errorMotivo, setErrorMotivo] = useState<string | null>(null)

  const abrirMutation = useAbrirCajaMutation()

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)
    setErrorMotivo(null)

    abrirMutation.mutate(
      {
        sedeId,
        montoApertura: 0,
        observaciones: observaciones.trim() || undefined,
      },
      {
        onSuccess: (caja: CajaSesionDTO) => {
          alGuardar?.(caja)
          alCerrar()
        },
        onError: (err: unknown) => {
          // El mensaje del backend dice el motivo real (p. ej. "Ya existe una caja activa abierta para esta sede...").
          setErrorMotivo(mensajeError(err))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Abrir Turno de Caja</DialogTitle>
          <DialogDescription>
            Apertura de operaciones para {sedeNombre ?? sedeId}. La caja inicia en cero.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}
        {errorMotivo && (
          <Alert tono="error" titulo="No se pudo abrir el turno de caja">
            {errorMotivo}
          </Alert>
        )}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-2">
          <Field label="Observaciones de Apertura (opcional)">
            <Input
              id="observaciones"
              value={observaciones}
              onChange={(e) => setObservaciones(e.target.value)}
              placeholder="Nota opcional"
            />
          </Field>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={abrirMutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={abrirMutation.isPending}>
              {abrirMutation.isPending ? 'Abriendo...' : 'Confirmar y Abrir Turno'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
