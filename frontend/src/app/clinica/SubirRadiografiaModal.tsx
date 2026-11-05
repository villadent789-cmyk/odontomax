import { textoDeError } from '@/shared/http'
import { useState } from 'react'
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
import { useSubirRadiografiaMutation, type TipoEstudioRadiografia, type RadiografiaDTO } from './api-radiografias'
import { TIPOS_ESTUDIO } from './tiposEstudio'

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre?: string
  alGuardar?: (radiografia: RadiografiaDTO) => void
}

export function SubirRadiografiaModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  alGuardar,
}: Props) {
  const [tipoEstudio, setTipoEstudio] = useState<TipoEstudioRadiografia>('PANORAMICA')
  const [etapa, setEtapa] = useState<'INICIAL' | 'CONTROL' | 'FINAL'>('INICIAL')
  const [piezaFdi, setPiezaFdi] = useState<number | undefined>(undefined)
  const [diagnostico, setDiagnostico] = useState('')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  const subirMutation = useSubirRadiografiaMutation()

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (!archivo) {
      setErrorLocal('Debe seleccionar un archivo de imagen, DICOM o documento radiológico.')
      return
    }

    const formData = new FormData()
    formData.append('archivo', archivo)
    formData.append('pacienteId', pacienteId)
    formData.append('tipoEstudio', tipoEstudio)
    formData.append('etapa', etapa)
    if (piezaFdi) {
      formData.append('piezaFdi', String(piezaFdi))
    }
    if (diagnostico.trim()) {
      formData.append('diagnosticoRadiografico', diagnostico.trim())
    }

    subirMutation.mutate(formData, {
      onSuccess: (nueva) => {
        alGuardar?.(nueva)
        setArchivo(null)
        setDiagnostico('')
        setPiezaFdi(undefined)
        alCerrar()
      },
      onError: (err) => {
        setErrorLocal(
          textoDeError(err, 'Error al subir el estudio radiológico. Verifique la conexión con el almacenamiento R2.'),
        )
      },
    })
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Subir Estudio Radiológico / Imagen 3D</DialogTitle>
          <DialogDescription>
            Carga de imágenes maxilofaciales y estudios para {pacienteNombre ?? 'el paciente'}.
            Almacenamiento seguro en Cloudflare R2 con trazabilidad clínica.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          <Field label="Tipo de Estudio Radiológico">
            <select
              className="w-full rounded border border-outline bg-surface p-2 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
              value={tipoEstudio}
              onChange={(e) => setTipoEstudio(e.target.value as TipoEstudioRadiografia)}
            >
              {TIPOS_ESTUDIO.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.etiqueta} {t.villamax ? '★ Villamax 3D' : ''}
                </option>
              ))}
            </select>
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Etapa del Estudio">
              <select
                className="w-full rounded border border-outline bg-surface p-2 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
                value={etapa}
                onChange={(e) => setEtapa(e.target.value as 'INICIAL' | 'CONTROL' | 'FINAL')}
              >
                <option value="INICIAL">Inicial / Diagnóstico</option>
                <option value="CONTROL">Control Intermedio</option>
                <option value="FINAL">Final / Alta Clínica</option>
              </select>
            </Field>

            <Field label="Pieza FDI (opcional)" ayuda="Para periapicales (ej: 11, 26)">
              <Input
                type="number"
                min={11}
                max={85}
                value={piezaFdi ?? ''}
                onChange={(e) => setPiezaFdi(e.target.value ? Number(e.target.value) : undefined)}
                placeholder="Ej: 21"
              />
            </Field>
          </div>

          <Field label="Diagnóstico radiográfico preliminar o hallazgos">
            <textarea
              className="w-full min-h-[70px] rounded border border-outline bg-surface p-2 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
              value={diagnostico}
              onChange={(e) => setDiagnostico(e.target.value)}
              placeholder="Ej: Reabsorción radicular leve en ápice, sin lesiones periapicales..."
            />
          </Field>

          <Field label="Archivo de imagen / radiografía *">
            <input
              type="file"
              accept="image/*,.pdf,.dcm"
              onChange={(e) => setArchivo(e.target.files?.[0] || null)}
              className="w-full text-xs text-on-surface-variant file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-primary-container file:text-on-primary-container hover:file:opacity-90"
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
              disabled={subirMutation.isPending}
            >
              {subirMutation.isPending ? 'Subiendo...' : 'Subir Estudio'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
