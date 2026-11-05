import { useState } from 'react'
import { Button } from '@/design-system/components/Button'
import { Badge } from '@/design-system/components/Badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { formatFechaHora } from '@/shared/format'
import {
  type ConsentimientoDTO,
  resolverUrlFirmaConsentimiento,
  resolverUrlPdfConsentimiento,
} from './api-consentimientos'

interface Props {
  abierto: boolean
  alCerrar: () => void
  consentimiento: ConsentimientoDTO | null
  pacienteNombre?: string
}

export function VisorConsentimientoModal({
  abierto,
  alCerrar,
  consentimiento,
  pacienteNombre,
}: Props) {
  const [errorCargaFirma, setErrorCargaFirma] = useState(false)

  if (!consentimiento) return null

  const urlFirma = resolverUrlFirmaConsentimiento(consentimiento)
  const urlPdf = resolverUrlPdfConsentimiento(consentimiento)

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <DialogTitle className="text-base font-bold text-on-surface">
              {consentimiento.titulo || 'Consentimiento Informado Suscrito'}
            </DialogTitle>
            <div className="flex items-center gap-2">
              <Badge tono="primario">✓ Firmado y Vigente</Badge>
              <Badge tono="neutro">{consentimiento.tipo}</Badge>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          {/* Ficha de Trazabilidad del Firmante */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-surface-container-low/80 p-3.5 rounded-xl border border-outline-variant/40 text-xs">
            <div>
              <span className="text-on-surface-variant block text-xs">Firmante:</span>
              <span className="font-semibold text-on-surface truncate block" title={consentimiento.nombreFirmante || pacienteNombre}>
                {consentimiento.nombreFirmante || pacienteNombre || 'Titular'}
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">DNI / Documento:</span>
              <span className="font-semibold text-on-surface font-mono">
                {consentimiento.documentoFirmante || '—'}
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">Condición / Parentesco:</span>
              <span className="font-semibold text-on-surface">
                {consentimiento.parentescoFirmante || 'Titular'}
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant block text-xs">Fecha y Hora:</span>
              <span className="font-semibold text-on-surface font-mono">
                {formatFechaHora(consentimiento.firmadoEn)}
              </span>
            </div>
          </div>

          {/* Texto Legal Aceptado */}
          {consentimiento.contenidoAceptado && (
            <div className="space-y-1.5">
              <span className="text-xs font-semibold text-on-surface">
                Texto Legal del Documento Aceptado:
              </span>
              <div className="max-h-40 overflow-y-auto rounded-lg border border-outline-variant/60 bg-surface-container-low/60 p-3 text-xs text-on-surface leading-relaxed whitespace-pre-wrap font-sans">
                {consentimiento.contenidoAceptado}
              </div>
            </div>
          )}

          {/* Firma Manuscrita o Foto del Consentimiento en Papel */}
          <div className="space-y-1.5">
            <span className="text-xs font-semibold text-on-surface">
              Firma o Foto del Documento Físico Registrado:
            </span>
            <div className="rounded-xl border border-outline-variant/60 bg-white p-3 flex flex-col items-center justify-center min-h-[140px] shadow-xs">
              {!errorCargaFirma ? (
                <img
                  src={urlFirma}
                  alt={`Firma de ${consentimiento.nombreFirmante || 'paciente'}`}
                  className="max-h-60 max-w-full object-contain rounded"
                  onError={() => setErrorCargaFirma(true)}
                />
              ) : (
                <div className="p-4 text-center text-on-surface-variant">
                  <div className="text-3xl mb-1">✍️</div>
                  <p className="font-semibold text-xs text-on-surface">Firma Digital Registrada</p>
                  <p className="text-xs text-on-surface-variant mt-0.5">
                    Suscrito por {consentimiento.nombreFirmante} (DNI: {consentimiento.documentoFirmante})
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Acciones de pie de diálogo */}
          <div className="flex flex-col-reverse sm:flex-row sm:justify-between items-center gap-2 pt-3 border-t border-outline-variant/40">
            <a
              href={urlPdf}
              target="_blank"
              rel="noreferrer"
              className="w-full sm:w-auto"
            >
              <Button variant="secundario" className="w-full sm:w-auto text-xs py-1.5">
                📥 Descargar PDF Oficial Firmado
              </Button>
            </a>

            <Button variant="primario" onClick={alCerrar} className="w-full sm:w-auto text-xs py-1.5">
              Cerrar
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
