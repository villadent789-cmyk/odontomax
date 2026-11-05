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
import { inputClases } from '@/design-system/components/Input'
import { mensajeError } from '@/shared/errores'
import { UNIDADES, useGuardarSede, type SedeAdmin } from './api-sedes'

interface Props {
  abierto: boolean
  /** Sede a editar; `null` = crear una nueva. */
  sede: SedeAdmin | null
  alCerrar: () => void
}

export function SedeFormModal({ abierto, sede, alCerrar }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-xl">
        <Formulario sede={sede} alCerrar={alCerrar} />
      </DialogContent>
    </Dialog>
  )
}

/** El estado vive dentro del diálogo: se reinicia cada vez que se abre. */
function Formulario({ sede, alCerrar }: Omit<Props, 'abierto'>) {
  const guardar = useGuardarSede()
  const editando = !!sede
  const [nombre, setNombre] = useState(sede?.nombre ?? '')
  const [direccion, setDireccion] = useState(sede?.direccion ?? '')
  const [sillones, setSillones] = useState(String(sede?.sillones ?? 1))
  const [telefono, setTelefono] = useState(sede?.telefono ?? '')
  const [whatsapp, setWhatsapp] = useState(sede?.whatsapp ?? '')
  const [email, setEmail] = useState(sede?.email ?? '')
  const [horario, setHorario] = useState(sede?.horarioTexto ?? '')
  const [descripcion, setDescripcion] = useState(sede?.descripcion ?? '')
  const [unidad, setUnidad] = useState<'VILLADENT' | 'VILLAMAX'>(sede?.unidadDeNegocio ?? 'VILLADENT')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const n = Number(sillones)
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Escribe el nombre de la sede.'
    if (!direccion.trim()) nuevos.direccion = 'Escribe la dirección.'
    if (!Number.isInteger(n) || n < 1) nuevos.sillones = 'Indica cuántos sillones tiene (al menos 1).'
    if (email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) nuevos.email = 'Escribe un correo válido.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await guardar.mutateAsync({
        id: sede?.id,
        // Siempre se envían todos los campos del formulario: el backend deja en blanco los que falten.
        cuerpo: {
          nombre: nombre.trim(),
          direccion: direccion.trim(),
          sillones: n,
          telefono: telefono.trim(),
          whatsapp: whatsapp.trim(),
          email: email.trim(),
          horarioTexto: horario.trim(),
          descripcion: descripcion.trim(),
          unidadDeNegocio: unidad,
        },
      })
      toast(editando ? `Sede actualizada: ${nombre.trim()}` : `Sede creada: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{editando ? 'Editar sede' : 'Nueva sede'}</DialogTitle>
        <DialogDescription>
          {editando
            ? 'Cambia los datos que ven el personal y los pacientes en la web.'
            : 'La sede queda activa al crearla y aparece en el selector de sedes y en la web pública.'}
        </DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo={editando ? 'No se pudo guardar la sede' : 'No se pudo crear la sede'}>
          {errorEnvio}
        </Alert>
      )}

      <Field label="Nombre de la sede" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} autoComplete="off" ref={enfocarAlMontar} />
      </Field>
      <Field label="Dirección" error={errores.direccion}>
        <Input value={direccion} onChange={(e) => setDireccion(e.target.value)} autoComplete="off" />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Sillones" error={errores.sillones}>
          <Input type="number" min={1} step={1} value={sillones} onChange={(e) => setSillones(e.target.value)} inputMode="numeric" />
        </Field>
        <Field label="Unidad de negocio">
          <select className={inputClases} value={unidad} onChange={(e) => setUnidad(e.target.value as 'VILLADENT' | 'VILLAMAX')}>
            {UNIDADES.map((u) => (
              <option key={u.valor} value={u.valor}>
                {u.etiqueta}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Teléfono (opcional)">
          <Input type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} inputMode="tel" />
        </Field>
        <Field label="WhatsApp (opcional)" ayuda="Con código de país, ej. 51943672062.">
          <Input type="tel" value={whatsapp} onChange={(e) => setWhatsapp(e.target.value)} inputMode="tel" />
        </Field>
      </div>
      <Field label="Correo (opcional)" error={errores.email}>
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
      </Field>
      <Field label="Horario de atención (opcional)" ayuda='Texto libre, ej. "Lun - Sáb: 9:00 am - 8:00 pm".'>
        <Input value={horario} onChange={(e) => setHorario(e.target.value)} />
      </Field>
      <Field label="Descripción (opcional)">
        <textarea className={`${inputClases} h-auto min-h-20 py-2`} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </Field>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={guardar.isPending}>
          {editando ? 'Guardar cambios' : 'Crear sede'}
        </Button>
      </DialogFooter>
    </form>
  )
}
