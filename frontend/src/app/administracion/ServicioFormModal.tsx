import { useId, useState } from 'react'
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
import { useSesion } from '@/shared/auth'
import { useGuardarServicio, type ServicioAdmin } from './api-servicios'

interface Props {
  abierto: boolean
  /** Servicio a editar; `null` = crear uno nuevo. */
  servicio: ServicioAdmin | null
  /** Especialidades ya usadas en el catálogo, para sugerirlas y no escribir la misma de dos formas. */
  especialidades: string[]
  alCerrar: () => void
}

export function ServicioFormModal({ abierto, servicio, especialidades, alCerrar }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-xl">
        <Formulario servicio={servicio} especialidades={especialidades} alCerrar={alCerrar} />
      </DialogContent>
    </Dialog>
  )
}

/** El estado vive dentro del diálogo: se reinicia cada vez que se abre. */
function Formulario({ servicio, especialidades, alCerrar }: Omit<Props, 'abierto'>) {
  const guardar = useGuardarServicio()
  const listaId = useId()
  const editando = !!servicio
  const [nombre, setNombre] = useState(servicio?.nombre ?? '')
  const [especialidad, setEspecialidad] = useState(servicio?.especialidad ?? '')
  const [duracion, setDuracion] = useState(String(servicio?.duracionMin ?? 30))
  const [precio, setPrecio] = useState(servicio?.precio != null ? String(servicio.precio) : '')
  // La comisión para el doctor que deriva solo existe en los estudios de Villamax.
  const esVillamax = useSesion()?.negocioActivo === 'VILLAMAX'
  const [comision, setComision] = useState(servicio?.comisionPorcentaje != null ? String(servicio.comisionPorcentaje) : '')
  const [comisionFija, setComisionFija] = useState(servicio?.comisionFija != null ? String(servicio.comisionFija) : '')
  const [precioLeve, setPrecioLeve] = useState(servicio?.precioLeve != null ? String(servicio.precioLeve) : '')
  const [precioModerado, setPrecioModerado] = useState(servicio?.precioModerado != null ? String(servicio.precioModerado) : '')
  const [precioSevero, setPrecioSevero] = useState(servicio?.precioSevero != null ? String(servicio.precioSevero) : '')
  const [descripcion, setDescripcion] = useState(servicio?.descripcion ?? '')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const minutos = Number(duracion)
    const monto = precio.trim() === '' ? undefined : Number(precio)
    const porcentaje = comision.trim() === '' ? undefined : Number(comision)
    const fija = comisionFija.trim() === '' ? undefined : Number(comisionFija)
    const porGravedad = [precioLeve, precioModerado, precioSevero].map((v) => (v.trim() === '' ? undefined : Number(v)))
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Escribe el nombre del servicio.'
    else if (nombre.trim().length > 120) nuevos.nombre = 'El nombre no puede superar los 120 caracteres.'
    if (!especialidad.trim()) nuevos.especialidad = 'Indica la especialidad.'
    else if (especialidad.trim().length > 80) nuevos.especialidad = 'La especialidad no puede superar los 80 caracteres.'
    if (!Number.isInteger(minutos) || minutos < 5 || minutos > 480) nuevos.duracion = 'La duración va de 5 a 480 minutos.'
    if (monto !== undefined && (!Number.isFinite(monto) || monto < 0)) nuevos.precio = 'El precio no puede ser negativo.'
    if (porcentaje !== undefined && (!Number.isFinite(porcentaje) || porcentaje < 0 || porcentaje > 100)) {
      nuevos.comision = 'La comisión va de 0 a 100.'
    }
    if (fija !== undefined && (!Number.isFinite(fija) || fija < 0)) nuevos.comisionFija = 'La comisión fija no puede ser negativa.'
    if (porGravedad.some((v) => v !== undefined && (!Number.isFinite(v) || v < 0))) nuevos.gravedad = 'Los precios por gravedad no pueden ser negativos.'
    if (descripcion.trim().length > 1000) nuevos.descripcion = 'La descripción no puede superar los 1000 caracteres.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await guardar.mutateAsync({
        id: servicio?.id,
        // Se envían todos los campos: el backend deja en blanco el precio y la descripción que falten.
        cuerpo: {
          nombre: nombre.trim(),
          especialidad: especialidad.trim(),
          duracionMin: minutos,
          precio: monto,
          comisionPorcentaje: porcentaje,
          comisionFija: fija,
          precioLeve: porGravedad[0],
          precioModerado: porGravedad[1],
          precioSevero: porGravedad[2],
          descripcion: descripcion.trim(),
          orden: servicio?.orden,
        },
      })
      toast(editando ? `Servicio actualizado: ${nombre.trim()}` : `Servicio creado: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{editando ? 'Editar servicio' : 'Nuevo servicio'}</DialogTitle>
        <DialogDescription>
          {editando
            ? 'Cambia lo que ven recepción y los doctores al agendar y al armar tratamientos.'
            : 'El servicio queda activo al crearlo: se puede elegir de inmediato en los tratamientos clínicos y en la web de reservas.'}
        </DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo={editando ? 'No se pudo guardar el servicio' : 'No se pudo crear el servicio'}>
          {errorEnvio}
        </Alert>
      )}

      <Field label="Nombre del servicio" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} autoComplete="off" ref={enfocarAlMontar} />
      </Field>
      <Field label="Especialidad" error={errores.especialidad} ayuda="Elige una existente o escribe una nueva.">
        <Input value={especialidad} onChange={(e) => setEspecialidad(e.target.value)} list={listaId} autoComplete="off" />
      </Field>
      <datalist id={listaId}>
        {especialidades.map((e) => (
          <option key={e} value={e} />
        ))}
      </datalist>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Duración (minutos)" error={errores.duracion}>
          <Input type="number" min={5} max={480} step={5} value={duracion} onChange={(e) => setDuracion(e.target.value)} inputMode="numeric" />
        </Field>
        <Field label="Precio en S/ (opcional)" error={errores.precio} ayuda="Referencial: el monto final se acuerda en el plan de tratamiento.">
          <Input type="number" min={0} step="0.01" value={precio} onChange={(e) => setPrecio(e.target.value)} inputMode="decimal" />
        </Field>
      </div>
      {(esVillamax || servicio?.comisionFija != null) && (
        <Field
          label="Comisión fija por estudio (S/)"
          error={errores.comisionFija}
          ayuda="Monto fijo que se lleva el doctor externo por cada estudio (S/ 10 radiografía, S/ 40 tomografía). Manda sobre el porcentaje."
        >
          <Input type="number" min={0} step="0.5" value={comisionFija} onChange={(e) => setComisionFija(e.target.value)} inputMode="decimal" />
        </Field>
      )}
      {(esVillamax || servicio?.comisionPorcentaje != null) && (
        <Field
          label="Comisión para el doctor que deriva (%)"
          error={errores.comision}
          ayuda="Porcentaje del precio que se lleva el doctor externo que envía al paciente."
        >
          <Input type="number" min={0} max={100} step="0.5" value={comision} onChange={(e) => setComision(e.target.value)} inputMode="decimal" />
        </Field>
      )}
      <fieldset className="rounded-panel border border-outline-variant/50 p-3">
        <legend className="px-1 text-xs font-bold text-on-surface">Precio por pieza según la gravedad (opcional)</legend>
        <p className="mb-2 text-xs text-on-surface-variant">El doctor elige leve, moderado o severo al armar el plan y el costo por pieza se ajusta. Si dejas uno vacío se usa el precio general.</p>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Leve (S/)" error={errores.gravedad}>
            <Input type="number" min={0} step="0.01" value={precioLeve} onChange={(e) => setPrecioLeve(e.target.value)} inputMode="decimal" />
          </Field>
          <Field label="Moderado (S/)">
            <Input type="number" min={0} step="0.01" value={precioModerado} onChange={(e) => setPrecioModerado(e.target.value)} inputMode="decimal" />
          </Field>
          <Field label="Severo (S/)">
            <Input type="number" min={0} step="0.01" value={precioSevero} onChange={(e) => setPrecioSevero(e.target.value)} inputMode="decimal" />
          </Field>
        </div>
      </fieldset>
      <Field label="Descripción (opcional)" error={errores.descripcion}>
        <textarea className={`${inputClases} h-auto min-h-20 py-2`} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </Field>

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={guardar.isPending}>
          {editando ? 'Guardar cambios' : 'Crear servicio'}
        </Button>
      </DialogFooter>
    </form>
  )
}
