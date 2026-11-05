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
import {
  cuerpoDesdeDoctor,
  useActualizarSillonesDoctor,
  useEditarDoctor,
  useGuardarMetaDoctor,
  useMetaDoctor,
  type Doctor,
} from './api-administracion'
import type { OpcionSede } from './EditorHorarios'

interface Props {
  doctor: Doctor | null
  alCerrar: () => void
  sedes: readonly OpcionSede[]
  doctores: readonly Doctor[]
}

export function EditarDoctorModal({ doctor, alCerrar, sedes, doctores }: Props) {
  return (
    <Dialog open={!!doctor} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-xl">
        {doctor && <Formulario doctor={doctor} alCerrar={alCerrar} sedes={sedes} doctores={doctores} />}
      </DialogContent>
    </Dialog>
  )
}

function Formulario({
  doctor,
  alCerrar,
  sedes,
  doctores,
}: {
  doctor: Doctor
  alCerrar: () => void
  sedes: readonly OpcionSede[]
  doctores: readonly Doctor[]
}) {
  const editar = useEditarDoctor()
  const actualizarSillones = useActualizarSillonesDoctor()
  const [nombre, setNombre] = useState(doctor.nombre ?? '')
  const [especialidad, setEspecialidad] = useState(doctor.especialidad ?? '')
  const [cop, setCop] = useState(doctor.cop ?? '')
  const [telefono, setTelefono] = useState(doctor.telefono ?? '')
  const [elegidas, setElegidas] = useState<string[]>(doctor.sedes ?? [])
  const [sillones, setSillones] = useState<Record<string, string>>(
    Object.fromEntries(Object.entries(doctor.sillonesPorSede ?? {}).map(([sede, numero]) => [sede, String(numero)])),
  )
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  const metaGuardada = useMetaDoctor(doctor.id)
  const guardarMeta = useGuardarMetaDoctor()
  const [metaTexto, setMetaTexto] = useState<string | null>(null)
  const [comisionTexto, setComisionTexto] = useState<string | null>(null)
  const metaVisible = metaTexto ?? (metaGuardada.data?.metaMensual != null ? String(metaGuardada.data.metaMensual) : '')
  const comisionVisible =
    comisionTexto ?? (metaGuardada.data?.comisionExcedentePorcentaje != null ? String(metaGuardada.data.comisionExcedentePorcentaje) : '')

  function alternarSede(id: string) {
    setElegidas((actual) => (actual.includes(id) ? actual.filter((s) => s !== id) : [...actual, id]))
  }

  async function guardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Escribe el nombre completo.'
    if (!especialidad.trim()) nuevos.especialidad = 'Indica la especialidad.'
    if (elegidas.length === 0) nuevos.sedes = 'Elige al menos una sede de atención.'
    const meta = metaVisible.trim() === '' ? undefined : Number(metaVisible)
    const comision = comisionVisible.trim() === '' ? undefined : Number(comisionVisible)
    if (meta !== undefined && (!Number.isFinite(meta) || meta < 0)) nuevos.meta = 'La meta debe ser un monto de 0 o más.'
    if (comision !== undefined && (!Number.isFinite(comision) || comision < 0 || comision > 100)) {
      nuevos.comision = 'La comisión debe estar entre 0 y 100.'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return
    try {
      await editar.mutateAsync({
        id: doctor.id!,
        cuerpo: cuerpoDesdeDoctor(doctor, {
          nombre: nombre.trim(),
          especialidad: especialidad.trim(),
          cop: cop.trim() || undefined,
          telefono: telefono.trim() || undefined,
          sedes: elegidas,
        }),
      })
      const sedesEditables = elegidas.filter((sedeId) => sedes.some((sede) => sede.id === sedeId))
      const sillonesPorSede = Object.fromEntries(
        sedesEditables
          .filter((sedeId) => sillones[sedeId])
          .map((sedeId) => [sedeId, Number(sillones[sedeId])]),
      )
      const sedesSinSillon = sedesEditables.filter((sedeId) => !sillones[sedeId])
      const cambiaronSillones =
        Object.entries(sillonesPorSede).some(([sedeId, numero]) => doctor.sillonesPorSede?.[sedeId] !== numero) ||
        sedesSinSillon.some((sedeId) => doctor.sillonesPorSede?.[sedeId] != null)
      if (cambiaronSillones) {
        try {
          await actualizarSillones.mutateAsync({ id: doctor.id!, cuerpo: { sillonesPorSede, sedesSinSillon } })
        } catch (error) {
          setErrorEnvio(`Perfil guardado, pero no se pudieron guardar los sillones: ${mensajeError(error)}`)
          return
        }
      }
      const metaAnterior = metaGuardada.data?.metaMensual ?? undefined
      const comisionAnterior = metaGuardada.data?.comisionExcedentePorcentaje ?? undefined
      if (meta !== metaAnterior || comision !== comisionAnterior) {
        try {
          await guardarMeta.mutateAsync({
            id: doctor.id!,
            cuerpo: { metaMensual: meta, comisionExcedentePorcentaje: comision },
          })
        } catch (error) {
          setErrorEnvio(`Perfil guardado, pero no se pudo guardar la meta: ${mensajeError(error)}`)
          return
        }
      }
      toast(`Perfil actualizado: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={guardar} className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Editar perfil</DialogTitle>
        <DialogDescription>{doctor.email ?? 'Doctor sin correo registrado'}</DialogDescription>
      </DialogHeader>
      {errorEnvio && <Alert tono="error" titulo="No se pudo guardar el perfil">{errorEnvio}</Alert>}
      <Field label="Nombre completo" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </Field>
      <Field label="Especialidad" error={errores.especialidad}>
        <Input value={especialidad} onChange={(e) => setEspecialidad(e.target.value)} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="COP (opcional)">
          <Input value={cop} onChange={(e) => setCop(e.target.value)} inputMode="numeric" />
        </Field>
        <Field label="Teléfono (opcional)">
          <Input type="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} inputMode="tel" />
        </Field>
      </div>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-bold text-on-surface">Sedes donde atiende</legend>
        {sedes.map((s) => (
          <label key={s.id} className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={elegidas.includes(s.id)} onChange={() => alternarSede(s.id)} />
            {s.nombre}
          </label>
        ))}
        {errores.sedes && <p className="text-sm font-bold text-error">{errores.sedes}</p>}
        <p className="text-sm text-on-surface-variant">
          Quitar una sede no borra sus turnos: revísalos en "Configurar horarios".
        </p>
      </fieldset>
      <fieldset className="flex flex-col gap-3">
        <legend className="text-sm font-bold text-on-surface">Sillón fijo por sede</legend>
        {sedes.filter((sede) => elegidas.includes(sede.id)).map((sede) => (
          <Field key={sede.id} label={sede.nombre}>
            <select
              aria-label={`Sillón en ${sede.nombre}`}
              className="w-full rounded-control border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
              value={sillones[sede.id] ?? ''}
              onChange={(e) => setSillones((actuales) => ({ ...actuales, [sede.id]: e.target.value }))}
            >
              <option value="">Sin asignar</option>
              {Array.from({ length: sede.sillones ?? 0 }, (_, i) => i + 1).map((numero) => {
                const ocupante = doctores.find(
                  (otro) => otro.id !== doctor.id && otro.sillonesPorSede?.[sede.id] === numero,
                )
                return (
                  <option key={numero} value={numero} disabled={!!ocupante}>
                    Sillón {numero}{ocupante ? ` (asignado a ${ocupante.nombre})` : ''}
                  </option>
                )
              })}
            </select>
          </Field>
        ))}
      </fieldset>
      <fieldset className="flex flex-col gap-3">
        <legend className="text-sm font-bold text-on-surface">Meta y comisión (solo informativo)</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Meta mensual (S/)" error={errores.meta}>
            <Input
              value={metaVisible}
              onChange={(e) => setMetaTexto(e.target.value)}
              inputMode="decimal"
              placeholder="Ej. 20000"
            />
          </Field>
          <Field label="Comisión sobre el excedente (%)" error={errores.comision}>
            <Input
              value={comisionVisible}
              onChange={(e) => setComisionTexto(e.target.value)}
              inputMode="decimal"
              placeholder="Ej. 10"
            />
          </Field>
        </div>
        <p className="text-sm text-on-surface-variant">
          La meta se mide sobre lo cobrado en el mes. Si la supera, la comisión estimada es ese porcentaje del excedente.
          Déjalos vacíos para no usar meta.
        </p>
      </fieldset>
      <DialogFooter>
        <Button
          type="button"
          variant="secundario"
          onClick={alCerrar}
          disabled={editar.isPending || actualizarSillones.isPending}
        >
          Cancelar
        </Button>
        <Button type="submit" cargando={editar.isPending || actualizarSillones.isPending}>
          Guardar cambios
        </Button>
      </DialogFooter>
    </form>
  )
}
