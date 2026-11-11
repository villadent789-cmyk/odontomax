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
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { mensajeError } from '@/shared/errores'
import { useCrearDoctorInvitado } from './api-administracion'
import type { OpcionSede } from './EditorHorarios'

interface Props {
  abierto: boolean
  alCerrar: () => void
  sedes: readonly OpcionSede[]
}

export function NuevoDoctorInvitadoModal({ abierto, alCerrar, sedes }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">
        <Formulario alCerrar={alCerrar} sedes={sedes} />
      </DialogContent>
    </Dialog>
  )
}

/** Doctor que viene por cita previa: sin cuenta, sin horario ni sillón fijo. Se registra una vez y queda siempre disponible para agendar. */
function Formulario({ alCerrar, sedes }: Omit<Props, 'abierto'>) {
  const crear = useCrearDoctorInvitado()
  const [nombre, setNombre] = useState('')
  const [especialidad, setEspecialidad] = useState('')
  const [cop, setCop] = useState('')
  const [elegidas, setElegidas] = useState<string[]>(sedes.length === 1 ? [sedes[0].id] : [])
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  async function guardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const nuevos: Record<string, string> = {}
    if (nombre.trim().length < 3) nuevos.nombre = 'Escribe el nombre completo.'
    if (!especialidad.trim()) nuevos.especialidad = 'Escribe la especialidad (p. ej. Implantes).'
    if (elegidas.length === 0) nuevos.sedes = 'Elige al menos una sede.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return
    try {
      await crear.mutateAsync({
        nombre: nombre.trim(),
        especialidad: especialidad.trim(),
        cop: cop.trim() || undefined,
        sedes: elegidas,
        invitado: true,
      })
      toast(`Doctor invitado registrado: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Nuevo doctor invitado</DialogTitle>
        <DialogDescription>
          Para especialistas que vienen por cita previa. No tiene cuenta ni horario fijo: recepción lo elige al agendar la cita y pone la
          fecha y la hora acordadas.
        </DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo="No se pudo registrar al doctor">
          {errorEnvio}
        </Alert>
      )}

      <Field label="Nombre completo" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} autoComplete="off" ref={enfocarAlMontar} />
      </Field>
      <Field label="Especialidad" error={errores.especialidad}>
        <Input value={especialidad} onChange={(e) => setEspecialidad(e.target.value)} maxLength={80} autoComplete="off" placeholder="Ej. Implantes, Odontopediatría" />
      </Field>
      <Field label="COP (opcional)">
        <Input value={cop} onChange={(e) => setCop(e.target.value)} maxLength={20} autoComplete="off" />
      </Field>

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-semibold text-on-surface">Sedes donde atiende</legend>
        {sedes.map((s) => (
          <label key={s.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={elegidas.includes(s.id)}
              onChange={(e) => setElegidas(e.target.checked ? [...elegidas, s.id] : elegidas.filter((x) => x !== s.id))}
            />
            {s.nombre}
          </label>
        ))}
        {errores.sedes && <p className="text-sm font-bold text-error">{errores.sedes}</p>}
      </fieldset>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={crear.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={crear.isPending}>
          Registrar doctor
        </Button>
      </DialogFooter>
    </form>
  )
}
