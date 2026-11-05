import { Button } from '@/design-system/components/Button'
import { Badge } from '@/design-system/components/Badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { formatFecha } from '@/shared/format'
import { resolverUrlRadiografia, type RadiografiaDTO } from './api-radiografias'

interface Props {
  abierto: boolean
  alCerrar: () => void
  radiografia: RadiografiaDTO | null
}

export function VisorRadiografiaModal({ abierto, alCerrar, radiografia }: Props) {
  if (!radiografia) return null

  const urlVisualizacion = resolverUrlRadiografia(radiografia)

  const esTomografia = radiografia.tipoEstudio === 'TOMOGRAFIA'
  const tamanoFormateado = radiografia.tamanoBytes
    ? `${(radiografia.tamanoBytes / (1024 * 1024)).toFixed(2)} MB`
    : 'N/A'

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <div className="flex items-center justify-between gap-3">
            <DialogTitle className="flex items-center gap-2">
              <span>{radiografia.nombreArchivo || 'Estudio Radiológico'}</span>
              <Badge tono={esTomografia ? 'villamax' : 'primario'}>
                {radiografia.tipoEstudio}
              </Badge>
              <Badge tono="neutro">{radiografia.etapa}</Badge>
            </DialogTitle>
          </div>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          {/* Contenedor de Imagen o Visor */}
          <div className="relative min-h-[300px] max-h-[500px] w-full overflow-hidden rounded-xl border border-outline-variant/40 bg-slate-950 shadow-inner flex items-center justify-center">
            {esTomografia ? (
              <div className="text-center p-8 space-y-3">
                <div className="text-5xl">🔬</div>
                <h4 className="text-lg font-bold text-white">Tomografía Cone Beam 3D (Villamax)</h4>
                <p className="text-xs text-neutral-400 max-w-md mx-auto">
                  Estudio volumétrico multislice FOV sincronizado con estación Villamax 3D.
                </p>
                {urlVisualizacion && (
                  <a
                    href={urlVisualizacion}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-block"
                  >
                    <Button variant="villamax">
                      Abrir en Visor DICOM 3D Web
                    </Button>
                  </a>
                )}
              </div>
            ) : urlVisualizacion ? (
              <img
                src={urlVisualizacion}
                alt={radiografia.nombreArchivo}
                className="max-h-[480px] w-auto object-contain"
                onError={(e) => {
                  // Fallback visual si el URL local simulado no carga imagen directa
                  const target = e.currentTarget
                  target.style.display = 'none'
                  if (target.parentElement) {
                    target.parentElement.innerHTML = `
                      <div class="p-8 text-center text-neutral-300">
                        <div class="text-4xl mb-2">🦷</div>
                        <p class="font-bold text-sm">Vista Previa Radiográfica</p>
                        <p class="text-xs text-neutral-400 mt-1">${radiografia.nombreArchivo}</p>
                      </div>
                    `
                  }
                }}
              />
            ) : (
              <p className="text-sm text-neutral-400">Sin vista previa disponible.</p>
            )}
          </div>

          {/* Detalles del estudio */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs bg-surface-container-low/80 p-3.5 rounded-xl border border-outline-variant/40">
            <div>
              <span className="text-on-surface-variant block text-xs">Fecha de Toma:</span>
              <span className="font-semibold text-on-surface">{formatFecha(radiografia.fechaToma)}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">Pieza FDI:</span>
              <span className="font-semibold text-on-surface font-mono">
                {radiografia.piezaFdi ? `Pieza ${radiografia.piezaFdi}` : 'General / Cuadrante'}
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">Tamaño de archivo:</span>
              <span className="font-semibold text-on-surface font-mono">{tamanoFormateado}</span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">ID de Registro:</span>
              <span className="font-mono text-on-surface truncate block" title={radiografia.id}>
                {radiografia.id.substring(0, 8)}...
              </span>
            </div>
          </div>

          {radiografia.diagnosticoRadiografico && (
            <div className="rounded border border-outline bg-surface-container-lowest p-3">
              <span className="text-xs font-bold text-on-surface block mb-1">
                Informe / Diagnóstico Radiográfico:
              </span>
              <p className="text-xs text-on-surface leading-relaxed">
                {radiografia.diagnosticoRadiografico}
              </p>
            </div>
          )}

          <div className="flex justify-between items-center pt-2 border-t border-outline">
            {urlVisualizacion ? (
              <a href={urlVisualizacion} download target="_blank" rel="noreferrer">
                <Button variant="secundario" className="text-xs">
                  📥 Descargar Archivo Original
                </Button>
              </a>
            ) : <div />}

            <Button variant="primario" onClick={alCerrar}>
              Cerrar Visor
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
