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
import { mensajeError } from '@/shared/errores'
import { useSolicitarCorreccion } from './api-correcciones'

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  actual: { nombres?: string; apellidos?: string; dni?: string; fechaNacimiento?: string }
}

/** Pide al dueño corregir el nombre, el DNI o la fecha de nacimiento; no cambia nada hasta que él lo autorice. */
export function SolicitarCorreccionModal({ abierto, alCerrar, pacienteId, actual }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">
        {abierto && <Formulario alCerrar={alCerrar} pacienteId={pacienteId} actual={actual} />}
      </DialogContent>
    </Dialog>
  )
}

function Formulario({ alCerrar, pacienteId, actual }: Omit<Props, 'abierto'>) {
  const solicitar = useSolicitarCorreccion(pacienteId)
  const [nombres, setNombres] = useState(actual.nombres ?? '')
  const [apellidos, setApellidos] = useState(actual.apellidos ?? '')
  const [dni, setDni] = useState(actual.dni ?? '')
  const [fecha, setFecha] = useState(actual.fechaNacimiento ?? '')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const cambios = {
      nombres: nombres.trim() !== (actual.nombres ?? '') ? nombres.trim() : undefined,
      apellidos: apellidos.trim() !== (actual.apellidos ?? '') ? apellidos.trim() : undefined,
      dni: dni.trim() !== (actual.dni ?? '') ? dni.trim() : undefined,
      fechaNacimiento: fecha && fecha !== (actual.fechaNacimiento ?? '') ? fecha : undefined,
    }
    if (!Object.values(cambios).some(Boolean)) {
      setError('Cambia al menos un dato respecto a lo que figura ahora.')
      return
    }
    if (cambios.dni && !/^\d{8}$/.test(cambios.dni)) {
      setError('El DNI debe tener 8 dígitos.')
      return
    }
    if (!motivo.trim()) {
      setError('Explica el motivo de la corrección.')
      return
    }
    try {
      await solicitar.mutateAsync({ ...cambios, motivo: motivo.trim() })
      toast('Solicitud enviada al dueño')
      alCerrar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Solicitar corrección de datos</DialogTitle>
        <DialogDescription>
          El nombre, el DNI y la fecha de nacimiento los cambia solo el dueño. Indica cómo deben quedar y por qué; los datos no
          cambian hasta que él lo autorice.
        </DialogDescription>
      </DialogHeader>

      {error && (
        <Alert tono="error" titulo="No se pudo enviar">
          {error}
        </Alert>
      )}

      <Field label="Nombres">
        <Input value={nombres} onChange={(e) => setNombres(e.target.value)} autoComplete="off" />
      </Field>
      <Field label="Apellidos">
        <Input value={apellidos} onChange={(e) => setApellidos(e.target.value)} autoComplete="off" />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="DNI">
          <Input value={dni} onChange={(e) => setDni(e.target.value.replace(/\D/g, '').slice(0, 8))} inputMode="numeric" autoComplete="off" />
        </Field>
        <Field label="Fecha de nacimiento">
          <Input type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        </Field>
      </div>
      <Field label="Motivo de la corrección *" ayuda="Ej. Se escribió mal el apellido al registrar.">
        <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} autoComplete="off" />
      </Field>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={solicitar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={solicitar.isPending}>
          Enviar solicitud
        </Button>
      </DialogFooter>
    </form>
  )
}
