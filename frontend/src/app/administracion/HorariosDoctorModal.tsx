import { useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Skeleton,
  toast,
} from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { useGuardarHorarios, useHorariosDoctor, type Doctor } from './api-administracion'
import { EditorHorarios, type OpcionSede } from './EditorHorarios'
import { cuerpoDesdeFilas, filasDesdeHorarios, validarHorarios, type FilaHorario } from './horarios'

interface Props {
  doctor: Doctor | null
  alCerrar: () => void
  sedes: readonly OpcionSede[]
}

export function HorariosDoctorModal({ doctor, alCerrar, sedes }: Props) {
  return (
    <Dialog open={!!doctor} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-2xl">{doctor && <Contenido doctor={doctor} alCerrar={alCerrar} sedes={sedes} />}</DialogContent>
    </Dialog>
  )
}

function Contenido({ doctor, alCerrar, sedes }: { doctor: Doctor; alCerrar: () => void; sedes: readonly OpcionSede[] }) {
  const consulta = useHorariosDoctor(doctor.id)
  const guardar = useGuardarHorarios()
  // Edición del usuario; mientras no toque nada, las filas salen directamente de lo que devolvió la consulta.
  const [filasEditadas, setFilas] = useState<FilaHorario[] | null>(null)
  const filasConsultadas = useMemo(() => (consulta.data ? filasDesdeHorarios(consulta.data) : null), [consulta.data])
  const filas = filasEditadas ?? filasConsultadas
  const [aviso, setAviso] = useState<string | null>(null)

  async function enviar() {
    if (!filas) return
    const problema = validarHorarios(filas)
    setAviso(problema)
    if (problema) return
    try {
      await guardar.mutateAsync({ doctor, horarios: cuerpoDesdeFilas(filas) })
      toast(`Horario actualizado: ${doctor.nombre}`)
      alCerrar()
    } catch (error) {
      setAviso(mensajeError(error))
    }
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>Horarios de {doctor.nombre}</DialogTitle>
        <DialogDescription>
          Al guardar se reemplaza todo el horario semanal. Un turno por sede y día.
        </DialogDescription>
      </DialogHeader>

      {consulta.isPending && <Skeleton className="h-32 w-full" />}
      {consulta.isError && (
        <Alert tono="error" titulo="No se pudieron cargar los horarios">
          {mensajeError(consulta.error)}
        </Alert>
      )}
      {filas && (
        <EditorHorarios filas={filas} onChange={setFilas} sedes={sedes} sedePorDefecto={doctor.sedes?.[0]} />
      )}
      {aviso && <Alert tono="error" titulo={aviso} />}

      <DialogFooter>
        <Button variant="secundario" onClick={alCerrar} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button onClick={enviar} cargando={guardar.isPending} disabled={!filas}>
          Guardar horarios
        </Button>
      </DialogFooter>
    </>
  )
}
