import { useState, useEffect, useRef, useCallback } from 'react'
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
import { Badge } from '@/design-system/components/Badge'
import {
  usePlantillasConsentimiento,
  useRegistrarConsentimientoMutation,
  type ConsentimientoDTO,
  type PlantillaConsentimientoDTO,
} from './api-consentimientos'
import { textoDeError } from '@/shared/http'

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre: string
  pacienteDni: string
  alGuardar?: (consentimiento: ConsentimientoDTO) => void
}

export function FirmarConsentimientoModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  pacienteDni,
  alGuardar,
}: Props) {
  const { data: plantillas = [], isLoading: cargandoPlantillas } = usePlantillasConsentimiento()
  const registrarMutation = useRegistrarConsentimientoMutation()

  const [plantillaElegida, setPlantillaSeleccionada] = useState<PlantillaConsentimientoDTO | null>(null)
  // Mientras no se elija otra, se usa la primera plantilla disponible.
  const plantillaSeleccionada = plantillaElegida ?? plantillas[0] ?? null
  const [nombreFirmante, setNombreFirmante] = useState('')
  const [documentoFirmante, setDocumentoFirmante] = useState('')
  const [parentescoFirmante, setParentescoFirmante] = useState('Titular')
  const [aceptaTerminos, setAceptaTerminos] = useState(false)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)
  const [modalidadFirma, setModalidadFirma] = useState<'digital' | 'foto'>('digital')
  const [fotoConsentimientoBase64, setFotoConsentimientoBase64] = useState<string | null>(null)
  const [nombreArchivoFoto, setNombreArchivoFoto] = useState<string | null>(null)

  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [dibujando, setDibujando] = useState(false)
  const [haDibujado, setHaDibujado] = useState(false)

  useAlCambiar([abierto, pacienteNombre, pacienteDni], () => {
    if (abierto) {
      setNombreFirmante(pacienteNombre || '')
      setDocumentoFirmante(pacienteDni || '')
      setParentescoFirmante('Titular')
      setAceptaTerminos(false)
      setErrorLocal(null)
      setHaDibujado(false)
      setModalidadFirma('digital')
      setFotoConsentimientoBase64(null)
      setNombreArchivoFoto(null)
    }
  })

  function procesarArchivoFoto(file: File) {
    setErrorLocal(null)
    if (!file.type.startsWith('image/')) {
      setErrorLocal('Debe seleccionar un archivo de imagen (JPG, PNG o WEBP).')
      return
    }
    setNombreArchivoFoto(file.name)
    const reader = new FileReader()
    reader.onload = (e) => {
      const dataUrl = e.target?.result as string
      const img = new Image()
      img.onload = () => {
        const canvas = document.createElement('canvas')
        let width = img.width
        let height = img.height
        const maxDim = 1200
        if (width > maxDim || height > maxDim) {
          if (width > height) {
            height = Math.round((height * maxDim) / width)
            width = maxDim
          } else {
            width = Math.round((width * maxDim) / height)
            height = maxDim
          }
        }
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        if (ctx) {
          ctx.fillStyle = '#ffffff'
          ctx.fillRect(0, 0, width, height)
          ctx.drawImage(img, 0, 0, width, height)
          const pngBase64 = canvas.toDataURL('image/png')
          setFotoConsentimientoBase64(pngBase64)
        }
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(file)
  }

  const limpiarCanvas = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    setHaDibujado(false)
  }, [])

  // El lienzo se monta con el diálogo: se limpia un instante después de abrir o de cambiar de paciente.
  useEffect(() => {
    if (!abierto) return
    const temporizador = setTimeout(limpiarCanvas, 100)
    return () => clearTimeout(temporizador)
  }, [abierto, pacienteNombre, pacienteDni, limpiarCanvas])

  function iniciarDibujo(e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    setDibujando(true)
    setHaDibujado(true)

    const rect = canvas.getBoundingClientRect()
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

    ctx.beginPath()
    ctx.moveTo(clientX - rect.left, clientY - rect.top)
    ctx.strokeStyle = '#0f172a'
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
  }

  function trazarDibujo(e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) {
    if (!dibujando) return
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const rect = canvas.getBoundingClientRect()
    const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
    const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

    ctx.lineTo(clientX - rect.left, clientY - rect.top)
    ctx.stroke()
  }

  function finalizarDibujo() {
    setDibujando(false)
  }

  function obtenerFirmaBase64(): string {
    if (modalidadFirma === 'foto' && fotoConsentimientoBase64) {
      return fotoConsentimientoBase64
    }

    const canvas = canvasRef.current
    if (canvas && haDibujado) {
      return canvas.toDataURL('image/png')
    }

    // Si no dibujó manualmente en canvas pero aceptó la casilla, generar firma digital tipográfica formal
    const tempCanvas = document.createElement('canvas')
    tempCanvas.width = 400
    tempCanvas.height = 120
    const ctx = tempCanvas.getContext('2d')
    if (ctx) {
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, tempCanvas.width, tempCanvas.height)
      ctx.fillStyle = '#0f172a'
      ctx.font = 'italic bold 22px cursive, sans-serif'
      ctx.fillText(nombreFirmante || 'Firma Digital Autorizada', 20, 60)
      ctx.font = '10px monospace'
      ctx.fillStyle = '#64748b'
      ctx.fillText(`DNI: ${documentoFirmante} · ${new Date().toISOString()}`, 20, 95)
      return tempCanvas.toDataURL('image/png')
    }

    return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAA='
  }

  function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setErrorLocal(null)

    if (!plantillaSeleccionada) {
      setErrorLocal('Debe seleccionar una plantilla de consentimiento válida.')
      return
    }

    if (!aceptaTerminos) {
      setErrorLocal('Debe marcar la casilla de aceptación y firma del consentimiento.')
      return
    }

    if (!nombreFirmante.trim() || !documentoFirmante.trim()) {
      setErrorLocal('El nombre y documento del firmante son obligatorios.')
      return
    }

    if (modalidadFirma === 'foto' && !fotoConsentimientoBase64) {
      setErrorLocal('Debe seleccionar o capturar una foto del consentimiento físico firmado.')
      return
    }

    const firmaBase64 = obtenerFirmaBase64()

    registrarMutation.mutate(
      {
        pacienteId,
        plantillaId: plantillaSeleccionada.id,
        tipo: plantillaSeleccionada.tipo,
        titulo: plantillaSeleccionada.titulo,
        contenidoAceptado: plantillaSeleccionada.contenido,
        firmaBase64,
        nombreFirmante: nombreFirmante.trim(),
        documentoFirmante: documentoFirmante.trim(),
        parentescoFirmante: parentescoFirmante.trim(),
      },
      {
        onSuccess: (guardado) => {
          alGuardar?.(guardado)
          alCerrar()
        },
        onError: (err) => {
          setErrorLocal(textoDeError(err, 'Error al registrar el consentimiento informado'))
        },
      }
    )
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Firma de Consentimiento Informado & Legal</DialogTitle>
          <DialogDescription>
            Validación de consentimiento clínico conforme a la Ley N° 29733 (Protección de Datos Personales)
            y la Norma Técnica de Salud del MINSA para atención odontológica.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          {/* Selector de plantilla */}
          <Field label="Plantilla Oficial de Consentimiento">
            {cargandoPlantillas ? (
              <p className="text-xs text-on-surface-variant">Cargando plantillas normativas...</p>
            ) : (
              <select
                className="w-full rounded border border-outline bg-surface p-2 text-xs text-on-surface focus:outline-none focus:ring-1 focus:ring-primary"
                value={plantillaSeleccionada?.id ?? ''}
                onChange={(e) => {
                  const encontrada = plantillas.find((p) => p.id === e.target.value)
                  if (encontrada) setPlantillaSeleccionada(encontrada)
                }}
              >
                {plantillas.map((p) => (
                  <option key={p.id} value={p.id}>
                    [{p.codigo}] {p.titulo} (v{p.version})
                  </option>
                ))}
              </select>
            )}
          </Field>

          {/* Vista previa del contenido del documento */}
          {plantillaSeleccionada && (
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-on-surface">Texto Legal del Documento:</span>
                <Badge tono="neutro">Versión {plantillaSeleccionada.version}</Badge>
              </div>
              <div className="max-h-40 overflow-y-auto rounded border border-outline bg-surface-container-low p-3 text-xs text-on-surface leading-relaxed whitespace-pre-wrap font-sans">
                {plantillaSeleccionada.contenido}
              </div>
            </div>
          )}

          {/* Datos del firmante */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Field label="Nombre del Firmante *">
              <Input
                value={nombreFirmante}
                onChange={(e) => setNombreFirmante(e.target.value)}
                placeholder="Nombre completo"
                required
              />
            </Field>

            <Field label="DNI / Documento *">
              <Input
                value={documentoFirmante}
                onChange={(e) => setDocumentoFirmante(e.target.value)}
                placeholder="8 dígitos"
                required
              />
            </Field>

            <Field label="Condición / Parentesco">
              <select
                className="w-full rounded-control border border-outline-variant/60 bg-surface-container-low/80 p-2.5 text-xs text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
                value={parentescoFirmante}
                onChange={(e) => setParentescoFirmante(e.target.value)}
              >
                <option value="Titular">Titular (Paciente)</option>
                <option value="Padre/Madre">Padre / Madre</option>
                <option value="Tutor Legal">Tutor / Apoderado Legal</option>
              </select>
            </Field>
          </div>

          {/* Modalidad de Firma: Digital en Pantalla vs. Foto en Papel */}
          <div className="space-y-2">
            <p className="text-xs font-semibold text-on-surface">
              Modalidad de Suscripción del Consentimiento:
            </p>
            <div className="grid grid-cols-2 gap-2 p-1 bg-surface-container-low rounded-xl border border-outline-variant/60">
              <button
                type="button"
                onClick={() => setModalidadFirma('digital')}
                className={`py-2 px-3 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                  modalidadFirma === 'digital'
                    ? 'bg-surface text-primary shadow-xs border border-outline-variant/40'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span>✍️</span>
                <span>Firma en Pantalla</span>
              </button>
              <button
                type="button"
                onClick={() => setModalidadFirma('foto')}
                className={`py-2 px-3 text-xs font-semibold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                  modalidadFirma === 'foto'
                    ? 'bg-surface text-primary shadow-xs border border-outline-variant/40'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span>📸</span>
                <span>Subir Foto / Escaneo en Papel</span>
              </button>
            </div>
          </div>

          {/* Opción A: Canvas de Firma Manuscrita */}
          {modalidadFirma === 'digital' && (
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-on-surface">
                  Trazo Manuscrito (Dibuje con ratón o pantalla táctil):
                </span>
                <button
                  type="button"
                  onClick={limpiarCanvas}
                  className="text-xs text-primary hover:underline font-medium"
                >
                  Limpiar trazo
                </button>
              </div>
              <div className="rounded-xl border border-outline-variant/60 bg-white overflow-hidden shadow-xs">
                <canvas
                  ref={canvasRef}
                  width={500}
                  height={110}
                  className="w-full h-[110px] cursor-crosshair touch-none"
                  onMouseDown={iniciarDibujo}
                  onMouseMove={trazarDibujo}
                  onMouseUp={finalizarDibujo}
                  onMouseLeave={finalizarDibujo}
                  onTouchStart={iniciarDibujo}
                  onTouchMove={trazarDibujo}
                  onTouchEnd={finalizarDibujo}
                />
              </div>
            </div>
          )}

          {/* Opción B: Foto / Escaneo del Documento Físico */}
          {modalidadFirma === 'foto' && (
            <div className="space-y-2 rounded-xl border border-outline-variant/60 bg-surface-container-low/40 p-3.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-on-surface">
                  Foto o Escaneo del Consentimiento Físico Firmado:
                </span>
                {fotoConsentimientoBase64 && (
                  <button
                    type="button"
                    onClick={() => {
                      setFotoConsentimientoBase64(null)
                      setNombreArchivoFoto(null)
                    }}
                    className="text-xs text-error hover:underline font-medium"
                  >
                    Eliminar foto
                  </button>
                )}
              </div>

              {!fotoConsentimientoBase64 ? (
                <div className="border-2 border-dashed border-outline-variant/60 rounded-xl p-5 text-center space-y-2 bg-surface hover:bg-surface-container-low/50 transition-colors">
                  <div className="text-3xl">📷</div>
                  <p className="text-xs text-on-surface font-medium">
                    Tome una foto o seleccione el archivo escaneado del consentimiento en papel
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    Formatos: JPG, PNG o WEBP (se optimiza automáticamente)
                  </p>
                  <label className="inline-block cursor-pointer pt-1">
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-acento text-on-acento text-xs font-medium rounded-lg shadow-xs hover:bg-primary/90">
                      📸 Seleccionar o Tomar Foto
                    </span>
                    <input
                      type="file"
                      accept="image/*"
                      capture="environment"
                      className="sr-only"
                      onChange={(e) => {
                        const file = e.target.files?.[0]
                        if (file) procesarArchivoFoto(file)
                      }}
                    />
                  </label>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="relative max-h-48 overflow-hidden rounded-lg border border-outline-variant/60 bg-slate-950 flex items-center justify-center p-2">
                    <img
                      src={fotoConsentimientoBase64}
                      alt="Consentimiento escaneado"
                      className="max-h-44 w-auto object-contain rounded"
                    />
                  </div>
                  <p className="text-xs text-on-surface-variant text-center font-mono truncate">
                    ✓ {nombreArchivoFoto || 'Foto de consentimiento lista'}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Checkbox de aceptación y firma */}
          <div className="rounded border border-primary/40 bg-primary/5 p-3">
            <label className="flex items-start gap-2.5 cursor-pointer">
              <input
                type="checkbox"
                checked={aceptaTerminos}
                onChange={(e) => setAceptaTerminos(e.target.checked)}
                className="mt-0.5 size-4 rounded text-primary focus:ring-primary"
              />
              <span className="text-xs text-on-surface font-medium leading-relaxed">
                Declaro que he leído (o me ha sido explicado detalladamente) el contenido del presente
                documento y manifiesto mi consentimiento libre, previo, expreso e inequívoco.
              </span>
            </label>
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-outline">
            <Button type="button" variant="secundario" onClick={alCerrar}>
              Cancelar
            </Button>
            <Button
              type="submit"
              variant="primario"
              disabled={registrarMutation.isPending || !aceptaTerminos}
            >
              {registrarMutation.isPending ? 'Registrando...' : 'Firmar y Registrar'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
