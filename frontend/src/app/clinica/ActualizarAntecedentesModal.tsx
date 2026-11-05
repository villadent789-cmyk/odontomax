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
import { useActualizarAntecedentesMutation } from './api-historia'
import { textoDeError } from '@/shared/http'

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre?: string
  alergiasIniciales?: string[] | string
  antecedentesIniciales?: string
  gestanteInicial?: boolean
  semanasIniciales?: number
  alGuardar?: () => void
}

export function ActualizarAntecedentesModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  alergiasIniciales,
  antecedentesIniciales,
  gestanteInicial = false,
  semanasIniciales,
  alGuardar,
}: Props) {
  const [alergias, setAlergias] = useState('')
  const [antecedentesMedicos, setAntecedentesMedicos] = useState('')
  const [esGestante, setEsGestante] = useState(false)
  const [semanasGestacion, setSemanasGestacion] = useState<number | undefined>(undefined)
  const [motivoCambio, setMotivoCambio] = useState('')
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const actualizarMutation = useActualizarAntecedentesMutation()

  const alergiasStr = Array.isArray(alergiasIniciales) ? alergiasIniciales.join(', ') : (alergiasIniciales || '')
  useAlCambiar([abierto, alergiasStr, antecedentesIniciales, gestanteInicial, semanasIniciales], () => {
    if (abierto) {
      setAlergias(alergiasStr)
      setAntecedentesMedicos(antecedentesIniciales || '')
      setEsGestante(Boolean(gestanteInicial))
      setSemanasGestacion(semanasIniciales)
      setMotivoCambio('')
      setErrorLocal(null)
    }
  })

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (!motivoCambio.trim()) {
      setErrorLocal('El motivo del cambio o actualización clínica es obligatorio por auditoría.')
      return
    }

    actualizarMutation.mutate(
      {
        pacienteId,
        dto: {
          alergias: alergias.trim() || undefined,
          antecedentesMedicos: antecedentesMedicos.trim() || undefined,
          esGestante,
          semanasGestacion: esGestante ? semanasGestacion : undefined,
          motivoCambio: motivoCambio.trim(),
        },
      },
      {
        onSuccess: () => {
          alGuardar?.()
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al actualizar antecedentes médicos'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Actualizar Antecedentes Médicos & Alergias</DialogTitle>
          <DialogDescription>
            Historial clínico de {pacienteNombre ?? 'el paciente'}. Cada actualización queda auditada con fecha,
            profesional y motivo de cambio legal.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          <Field
            label="Alergias conocidas (separadas por coma)"
            ayuda="Ej: Penicilina, Látex, AINEs, Anestésicos locales"
          >
            <Input
              value={alergias}
              onChange={(e) => setAlergias(e.target.value)}
              placeholder="Ej: Penicilina, Ibuprofeno..."
            />
          </Field>

          <Field
            label="Antecedentes médicos y enfermedades sistémicas"
            ayuda="Diabetes, hipertensión arterial, marcapasos, trastornos de coagulación..."
          >
            <textarea
              className="w-full min-h-[80px] rounded border border-outline bg-surface p-2.5 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
              value={antecedentesMedicos}
              onChange={(e) => setAntecedentesMedicos(e.target.value)}
              placeholder="Describa enfermedades preexistentes o cirugías relevantes..."
            />
          </Field>

          {/* Condición gestante */}
          <div className="rounded border border-outline bg-surface-container-low p-3 space-y-3">
            <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-on-surface">
              <input
                type="checkbox"
                checked={esGestante}
                onChange={(e) => setEsGestante(e.target.checked)}
                className="size-4 rounded text-primary focus:ring-primary"
              />
              <span>Condición: Paciente gestante</span>
            </label>

            {esGestante && (
              <Field label="Semanas de gestación aproximadas">
                <Input
                  type="number"
                  min={1}
                  max={42}
                  value={semanasGestacion ?? ''}
                  onChange={(e) => setSemanasGestacion(e.target.value ? Number(e.target.value) : undefined)}
                  placeholder="Ej: 14"
                />
              </Field>
            )}
          </div>

          <Field
            label="Motivo de la actualización / justificación clínica *"
            ayuda="Obligatorio para la auditoría de la historia clínica"
          >
            <Input
              value={motivoCambio}
              onChange={(e) => setMotivoCambio(e.target.value)}
              placeholder="Ej: Anamnesis inicial / Paciente refiere nueva reacción alérgica..."
              required
            />
          </Field>

          <div className="flex justify-end gap-2 pt-2 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar}>
              Cancelar
            </Button>
            <Button
              type="submit"
              variant="primario"
              disabled={actualizarMutation.isPending}
            >
              {actualizarMutation.isPending ? 'Guardando...' : 'Guardar Actualización'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
