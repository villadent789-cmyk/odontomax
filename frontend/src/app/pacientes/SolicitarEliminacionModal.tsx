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
import { useEliminarRadiografiaMutation } from '../clinica/api-radiografias'
import { mensajeError } from '@/shared/errores'
import { useSolicitarEliminacion, type TipoEliminacion } from './api-eliminaciones'

interface Props {
  abierto: boolean
  alCerrar: () => void
  tipo: TipoEliminacion
  objetivoId: string
  /** Cómo se llama lo que se quiere eliminar, para que quien pide lo vea claro (p. ej. «la radiografía foto.jpg»). */
  descripcion: string
  /** Solo para el dueño y solo radiografías: elimina de inmediato en vez de pedir autorización. */
  directo?: { pacienteId: string; alEliminar: () => void }
}

/** Pide al dueño eliminar una radiografía o un paciente; no se elimina nada hasta que él lo autorice. */
export function SolicitarEliminacionModal({ abierto, alCerrar, tipo, objetivoId, descripcion, directo }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">
        {abierto && <Formulario alCerrar={alCerrar} tipo={tipo} objetivoId={objetivoId} descripcion={descripcion} directo={directo} />}
      </DialogContent>
    </Dialog>
  )
}

function Formulario({ alCerrar, tipo, objetivoId, descripcion, directo }: Omit<Props, 'abierto'>) {
  const solicitar = useSolicitarEliminacion()
  const eliminarDirecto = useEliminarRadiografiaMutation()
  const enCurso = solicitar.isPending || eliminarDirecto.isPending
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (motivo.trim().length < 5) {
      setError('Explica el motivo de la eliminación (mínimo 5 caracteres).')
      return
    }
    try {
      if (directo) {
        await eliminarDirecto.mutateAsync({ id: objetivoId, motivo: motivo.trim(), pacienteId: directo.pacienteId })
        toast('Estudio eliminado')
        directo.alEliminar()
        return
      }
      await solicitar.mutateAsync({ tipo, objetivoId, motivo: motivo.trim() })
      toast('Solicitud enviada al dueño')
      alCerrar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{directo ? 'Eliminar definitivamente' : 'Solicitar eliminación'}</DialogTitle>
        <DialogDescription>
          {directo
            ? `Vas a eliminar ${descripcion} de forma permanente, incluido el archivo guardado. El motivo queda en la auditoría.`
            : `Quieres eliminar ${descripcion}. Solo el dueño puede eliminarlo: no se borra nada hasta que él lo autorice.`}
        </DialogDescription>
      </DialogHeader>

      {error && (
        <Alert tono="error" titulo={directo ? 'No se pudo eliminar' : 'No se pudo enviar'}>
          {error}
        </Alert>
      )}

      <Field label="Motivo de la eliminación *" ayuda="Ej. Se subió la foto de otro paciente.">
        <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} autoComplete="off" />
      </Field>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={enCurso}>
          Cancelar
        </Button>
        <Button type="submit" cargando={enCurso}>
          {directo ? 'Eliminar' : 'Enviar solicitud'}
        </Button>
      </DialogFooter>
    </form>
  )
}
