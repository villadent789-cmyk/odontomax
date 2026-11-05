import { HERRAMIENTAS_EN_PALETA, esDePiezaCompleta, esHallazgoRojo } from './herramientasOdontograma'
import { useState } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
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
import { useRegistrarPiezaOdontogramaMutation, type OdontogramaRegistroDTO } from './api-odontograma'
import { textoDeError } from '@/shared/http'
import { separarRadiografia, unirRadiografia } from './especificaciones'

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  piezaInicialFdi?: number
  registroActual?: OdontogramaRegistroDTO
  alGuardar?: () => void
}

/** Mismos estados y colores que el odontograma (norma): rojo caries, fractura, temporales y mal estado; azul lo demás. */
const ESTADOS_ODONTOGRAMA = HERRAMIENTAS_EN_PALETA.map((h) => ({
  id: h.id,
  label: h.label,
  color: esHallazgoRojo(h)
    ? 'border-red-500 bg-red-50 text-red-700'
    : h.id === 'sano'
      ? 'border-outline bg-surface-container-low text-on-surface'
      : 'border-blue-500 bg-blue-50 text-blue-700',
}))

const CARAS_DENTALES = [
  { id: 'V', nombre: 'Vestibular (V)', desc: 'Cara frontal hacia los labios/mejilla' },
  { id: 'O', nombre: 'Oclusal / Incisal (O)', desc: 'Cara masticatoria o borde incisal' },
  { id: 'P', nombre: 'Palatino / Lingual (P/L)', desc: 'Cara interna hacia el paladar/lengua' },
  { id: 'M', nombre: 'Mesial (M)', desc: 'Cara lateral hacia la línea media' },
  { id: 'D', nombre: 'Distal (D)', desc: 'Cara lateral opuesta a la línea media' },
]

export function EditarPiezaOdontogramaModal({
  abierto,
  alCerrar,
  pacienteId,
  piezaInicialFdi,
  registroActual,
  alGuardar,
}: Props) {
  const [piezaFdi, setPiezaFdi] = useState(String(piezaInicialFdi ?? 11))
  const [estado, setEstado] = useState('caries')
  const [carasSeleccionadas, setCarasSeleccionadas] = useState<string[]>([])
  const [observacion, setObservacion] = useState('')
  const [radiografico, setRadiografico] = useState(false)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const registrarMutation = useRegistrarPiezaOdontogramaMutation()

  const clavesRegistro = registroActual
    ? [registroActual.estado, registroActual.caras?.join(','), registroActual.observacion]
    : []
  useAlCambiar([abierto, piezaInicialFdi, Boolean(registroActual), ...clavesRegistro], () => {
    if (piezaInicialFdi) {
      setPiezaFdi(String(piezaInicialFdi))
    }
    if (registroActual) {
      setEstado(registroActual.estado?.toLowerCase() || 'caries')
      setCarasSeleccionadas(registroActual.caras || [])
      const separada = separarRadiografia(registroActual.observacion)
      setObservacion(separada.texto)
      setRadiografico(separada.radiografico)
    } else {
      setEstado('caries')
      setCarasSeleccionadas(['O'])
      setObservacion('')
      setRadiografico(false)
    }
    setErrorLocal(null)
  })

  function toggleCara(c: string) {
    setCarasSeleccionadas((prev) =>
      prev.includes(c) ? prev.filter((item) => item !== c) : [...prev, c]
    )
  }

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    const numFdi = parseInt(piezaFdi, 10)
    if (isNaN(numFdi) || numFdi < 11 || numFdi > 85) {
      setErrorLocal('El número de pieza dental FDI debe estar entre 11 y 85.')
      return
    }

    // Si es ausente o sano, no requiere caras específicas
    const carasAEnviar =
      ['ausente', 'sano'].includes(estado) || esDePiezaCompleta(estado) ? [] : carasSeleccionadas

    registrarMutation.mutate(
      {
        pacienteId,
        dto: {
          piezaFdi: numFdi,
          estado,
          caras: carasAEnviar,
          observacion: unirRadiografia(radiografico, observacion),
        },
      },
      {
        onSuccess: () => {
          alGuardar?.()
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al guardar el diagnóstico en el odontograma'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registrar Hallazgo en Odontograma</DialogTitle>
          <DialogDescription>
            Asiente la patología, restauración o condición anatómica de la pieza dental seleccionada.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Pieza Dental FDI *" ayuda="Ej. 18, 21, 36, 48">
              <Input
                id="piezaFdiInput"
                type="number"
                min="11"
                max="85"
                value={piezaFdi}
                onChange={(e) => setPiezaFdi(e.target.value)}
                placeholder="N° FDI"
                required
              />
            </Field>

            <div className="flex flex-col justify-end pb-2">
              <span className="text-xs text-on-surface-variant">
                Pieza:{' '}
                <strong className="text-primary font-mono text-base">#{piezaFdi}</strong>
              </span>
            </div>
          </div>

          {/* Diagnóstico / Condición Clínica */}
          <Field label="Hallazgo / Diagnóstico Clínico *">
            <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto pr-1">
              {ESTADOS_ODONTOGRAMA.map((est) => (
                <button
                  key={est.id}
                  type="button"
                  onClick={() => setEstado(est.id)}
                  className={`p-2.5 rounded-lg border text-xs text-left font-semibold transition-all ${
                    estado === est.id
                      ? `${est.color} ring-2 ring-primary ring-offset-1 shadow-sm`
                      : 'border-outline bg-surface-container-low text-on-surface-variant hover:bg-surface-container'
                  }`}
                >
                  {est.label}
                </button>
              ))}
            </div>
          </Field>

          {/* Caras Afectadas (solo si no es ausente/sano) */}
          {!['ausente', 'sano'].includes(estado) && (
            <Field label="Caras Afectadas" ayuda="Seleccione una o varias caras comprometidas">
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {CARAS_DENTALES.map((cara) => {
                  const seleccionada = carasSeleccionadas.includes(cara.id)
                  return (
                    <button
                      key={cara.id}
                      type="button"
                      onClick={() => toggleCara(cara.id)}
                      className={`p-2 rounded-lg border text-xs text-center font-medium transition-all ${
                        seleccionada
                          ? 'border-primary bg-primary/10 text-primary font-bold shadow-sm'
                          : 'border-outline bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
                      }`}
                      title={cara.desc}
                    >
                      {cara.nombre}
                    </button>
                  )
                })}
              </div>
            </Field>
          )}

          <label className="flex items-center gap-2 text-sm text-on-surface">
            <input type="checkbox" checked={radiografico} onChange={(e) => setRadiografico(e.target.checked)} />
            Hallazgo visto en radiografía
          </label>

          <Field label="Observación Clínica (opcional)">
            <Input
              id="observacionOdontograma"
              value={observacion}
              onChange={(e) => setObservacion(e.target.value)}
              placeholder="Ej. Caries profunda oclusal, no presenta sintomatología dolorosa"
            />
          </Field>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={registrarMutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={registrarMutation.isPending}>
              {registrarMutation.isPending ? 'Guardando...' : 'Asentar en Odontograma'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
