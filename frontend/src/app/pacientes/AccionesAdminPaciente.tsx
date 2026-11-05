import { useNavigate } from '@tanstack/react-router'
import { Archive, Download, Trash2 } from 'lucide-react'
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
  toast,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { descargarArchivo, nombreParaArchivo } from '@/shared/descargas'
import { HttpError } from '@/shared/http/errores'
import { mensajeError } from '@/shared/errores'
import { useArchivarPaciente, useEliminarPaciente, useExportarPaciente } from './api-pacientes'

const MOTIVOS = [
  { valor: 'DUPLICADA', etiqueta: 'Ficha duplicada / creada por error' },
  { valor: 'PRUEBA', etiqueta: 'Ficha de prueba o capacitación' },
  { valor: 'ANULACION', etiqueta: 'Paciente solicitó anulación de registro' },
  { valor: 'OTRO', etiqueta: 'Otro motivo (especificar)' },
] as const

type ValorMotivo = (typeof MOTIVOS)[number]['valor']

const MIN_DETALLE = 5

interface Props {
  pacienteId: string
  nombre: string
}

/**
 * Acciones del administrador general sobre un paciente: exportar su expediente (derecho de acceso) y eliminarlo de forma
 * permanente con un motivo que queda en la auditoría. Eliminar solo procede en fichas sin historia clínica ni pagos
 * (p. ej. creadas por error o duplicadas); si ya tiene historia, el mismo diálogo ofrece archivarla.
 */
export function AccionesAdminPaciente({ pacienteId, nombre }: Props) {
  const exportar = useExportarPaciente()
  const [errorExportar, setErrorExportar] = useState<string | null>(null)
  const [eliminando, setEliminando] = useState(false)

  async function descargar() {
    setErrorExportar(null)
    try {
      const expediente = await exportar.mutateAsync(pacienteId)
      const archivo = new Blob([JSON.stringify(expediente, null, 2)], { type: 'application/json' })
      descargarArchivo(archivo, `expediente-${nombreParaArchivo(nombre)}.json`)
      toast(`Expediente exportado: ${nombre}`)
    } catch (error) {
      setErrorExportar(mensajeError(error))
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-2" aria-label="Acciones de administración">
        <Button size="sm" variant="secundario" onClick={() => void descargar()} cargando={exportar.isPending}>
          <Download aria-hidden />
          Exportar expediente
        </Button>
        <Button size="sm" variant="peligro" onClick={() => setEliminando(true)}>
          <Trash2 aria-hidden />
          Eliminar paciente
        </Button>
      </div>
      {errorExportar && (
        <Alert tono="error" titulo="No se pudo exportar el expediente" onCerrar={() => setErrorExportar(null)}>
          {errorExportar}
        </Alert>
      )}
      {eliminando && <EliminarPacienteModal pacienteId={pacienteId} nombre={nombre} alCerrar={() => setEliminando(false)} />}
    </>
  )
}

function EliminarPacienteModal({ pacienteId, nombre, alCerrar }: Props & { alCerrar: () => void }) {
  const eliminar = useEliminarPaciente()
  const archivar = useArchivarPaciente()
  const navegar = useNavigate()
  const [opcion, setOpcion] = useState<ValorMotivo | ''>('')
  const [detalle, setDetalle] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)
  /** Mensaje del backend cuando el paciente ya tiene historia o pagos (409): ya no se puede eliminar, solo archivar. */
  const [conflicto, setConflicto] = useState<string | null>(null)
  const ocupado = eliminar.isPending || archivar.isPending

  /** Motivo exacto que se guarda: la opción elegida más el detalle (o solo el detalle si es "Otro motivo"). */
  function motivoFinal(): string | null {
    const nuevos: Record<string, string> = {}
    const elegido = MOTIVOS.find((m) => m.valor === opcion)
    const texto = detalle.trim()
    if (!elegido) nuevos.motivo = 'Elige el motivo.'
    else if (elegido.valor === 'OTRO' && texto.length < MIN_DETALLE) nuevos.detalle = `Describe el motivo (al menos ${MIN_DETALLE} caracteres).`
    setErrores(nuevos)
    if (!elegido || Object.keys(nuevos).length > 0) return null
    if (elegido.valor === 'OTRO') return texto
    return texto ? `${elegido.etiqueta}: ${texto}` : elegido.etiqueta
  }

  async function confirmarEliminar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const motivo = motivoFinal()
    if (!motivo) return
    try {
      await eliminar.mutateAsync({ id: pacienteId, motivo })
      toast(`Paciente eliminado: ${nombre}`)
      await navegar({ to: '/app/pacientes' })
    } catch (error) {
      if (error instanceof HttpError && error.status === 409) setConflicto(mensajeError(error))
      else setErrorEnvio(mensajeError(error))
    }
  }

  async function archivarFicha() {
    setErrorEnvio(null)
    const motivo = motivoFinal()
    if (!motivo) return
    try {
      await archivar.mutateAsync({ id: pacienteId, motivo })
      toast(`Ficha archivada: ${nombre}`)
      await navegar({ to: '/app/pacientes' })
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <Dialog open onOpenChange={(v) => !v && !ocupado && alCerrar()}>
      <DialogContent className="max-w-lg">
        <form onSubmit={confirmarEliminar} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>¿Eliminar a {nombre}?</DialogTitle>
            <DialogDescription>
              Se borra de forma permanente, con sus citas, odontograma, tratamientos y archivos. No se puede deshacer. El motivo queda
              registrado en la auditoría con tu usuario.
            </DialogDescription>
          </DialogHeader>

          {conflicto ? (
            <Alert tono="aviso" titulo="Esta ficha ya tiene historia y no se puede eliminar">
              {conflicto} Por norma técnica de salud, un expediente con registros solo se archiva: la historia clínica se conserva intacta.
            </Alert>
          ) : (
            <Alert tono="aviso" titulo="Solo para fichas sin historia">
              Si el paciente ya tiene notas clínicas, antecedentes, accesos a su historia o pagos, el sistema no lo elimina: ese
              expediente solo se archiva.
            </Alert>
          )}
          {errorEnvio && (
            <Alert tono="error" titulo={conflicto ? 'No se pudo archivar la ficha' : 'No se pudo eliminar al paciente'}>
              {errorEnvio}
            </Alert>
          )}

          <Field label="Motivo de eliminación" error={errores.motivo}>
            <select className={inputClases} value={opcion} onChange={(e) => setOpcion(e.target.value as ValorMotivo | '')}>
              <option value="">Elige un motivo…</option>
              {MOTIVOS.map((m) => (
                <option key={m.valor} value={m.valor}>
                  {m.etiqueta}
                </option>
              ))}
            </select>
          </Field>
          <Field
            label={opcion === 'OTRO' ? 'Detalle del motivo' : 'Detalle adicional (opcional)'}
            error={errores.detalle}
          >
            <textarea
              className={`${inputClases} h-auto min-h-20 py-2`}
              value={detalle}
              onChange={(e) => setDetalle(e.target.value)}
              maxLength={300}
            />
          </Field>

          <DialogFooter>
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={ocupado}>
              Cancelar
            </Button>
            {conflicto ? (
              <Button type="button" variant="primario" onClick={() => void archivarFicha()} cargando={archivar.isPending}>
                <Archive aria-hidden />
                Archivar ficha
              </Button>
            ) : (
              <Button type="submit" variant="peligro" cargando={eliminar.isPending}>
                Eliminar definitivamente
              </Button>
            )}
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
