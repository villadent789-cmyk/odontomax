import { useState } from 'react'
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
  toast,
} from '@/design-system'
import { useActualizarMiEspecialidad } from '@/app/administracion/api-administracion'
import { useEspecialistas } from '@/app/agenda/api-agenda'
import { mensajeError } from '@/shared/errores'

interface Props {
  abierto: boolean
  onCambio: (v: boolean) => void
  usuarioId?: string
  sedeId: string | null
}

/** El doctor corrige su propia especialidad; se refleja en la web pública y en el sistema. */
export function MiEspecialidadDialog({ abierto, onCambio, usuarioId, sedeId }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={onCambio}>
      <DialogContent className="max-w-md">
        {abierto && <Formulario alCerrar={() => onCambio(false)} usuarioId={usuarioId} sedeId={sedeId} />}
      </DialogContent>
    </Dialog>
  )
}

function Formulario({ alCerrar, usuarioId, sedeId }: { alCerrar: () => void; usuarioId?: string; sedeId: string | null }) {
  const especialistas = useEspecialistas(sedeId)
  const mia = especialistas.data?.find((e) => e.usuarioId === usuarioId)
  const guardar = useActualizarMiEspecialidad()
  const [texto, setTexto] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const valor = texto ?? mia?.especialidad ?? ''

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!valor.trim()) {
      setError('Escribe tu especialidad.')
      return
    }
    try {
      await guardar.mutateAsync(valor.trim())
      toast('Especialidad actualizada')
      alCerrar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Mi especialidad</DialogTitle>
        <DialogDescription>Es la que ven los pacientes en la web y el equipo en el sistema.</DialogDescription>
      </DialogHeader>
      {!especialistas.isLoading && !mia && <Alert tono="info" titulo="No encontramos tu ficha de doctor">Pide al administrador que revise tu cuenta.</Alert>}
      {error && (
        <Alert tono="error" titulo="No se pudo guardar">
          {error}
        </Alert>
      )}
      <Field label="Especialidad">
        <Input value={valor} onChange={(e) => setTexto(e.target.value)} maxLength={80} autoComplete="off" disabled={!mia} />
      </Field>
      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={guardar.isPending} disabled={!mia}>
          Guardar
        </Button>
      </DialogFooter>
    </form>
  )
}
