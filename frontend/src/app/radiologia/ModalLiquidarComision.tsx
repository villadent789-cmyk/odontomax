import { esOpcion } from '@/shared/opciones'
import { useState, useId } from 'react'
import { AlertCircle, CheckCircle2, MessageCircle } from 'lucide-react'
import { Button, Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, Input } from '@/design-system'
import type { ComisionRadiologicaDTO, ComisionRadiologicaLiquidarRequestDTO } from './types'

interface ModalLiquidarComisionProps {
  abierto: boolean
  onCerrar: () => void
  comision: ComisionRadiologicaDTO | null
  onLiquidar: (id: string, req: ComisionRadiologicaLiquidarRequestDTO) => Promise<void>
}

const METODOS_LIQUIDACION = ['YAPE', 'PLIN', 'EFECTIVO'] as const
type MetodoLiquidacion = (typeof METODOS_LIQUIDACION)[number]

export function ModalLiquidarComision({
  abierto,
  onCerrar,
  comision,
  onLiquidar,
}: ModalLiquidarComisionProps) {
  const uid = useId()
  const [metodoPago, setMetodoPago] = useState<MetodoLiquidacion>('YAPE')
  const [nota, setNota] = useState('')
  const [liquidando, setLiquidando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [liquidadoExitoso, setLiquidadoExitoso] = useState(false)

  if (!comision) return null

  const handleLiquidar = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      setLiquidando(true)
      setError(null)
      await onLiquidar(comision.id, {
        metodoPago,
        nota: nota.trim() || undefined,
      })
      setLiquidadoExitoso(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al liquidar la comisión')
    } finally {
      setLiquidando(false)
    }
  }

  const telefonoLimpio = comision.doctorDerivadorTelefono?.replace(/\D/g, '') ?? ''
  const telefonoWa = telefonoLimpio.startsWith('51') ? telefonoLimpio : `51${telefonoLimpio}`
  const mensajeWhatsApp = encodeURIComponent(
    `Estimado(a) ${comision.doctorDerivadorNombre}, Villamax Chilca le informa que se ha liquidado su comisión de S/ ${comision.montoComision.toFixed(2)} correspondiente al estudio radiológico (${comision.tipoEstudio}) del paciente ${comision.pacienteNombre} vía ${metodoPago}. ¡Muchas gracias por su confianza y trabajo conjunto!`
  )
  const urlWhatsApp = `https://wa.me/${telefonoWa}?text=${mensajeWhatsApp}`

  return (
    <Dialog
      open={abierto}
      onOpenChange={(open) => {
        if (!open) {
          setLiquidadoExitoso(false)
          onCerrar()
        }
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {liquidadoExitoso ? '¡Comisión Liquidada con Éxito!' : 'Liquidar Comisión de Radiología'}
          </DialogTitle>
          <DialogDescription>
            {liquidadoExitoso
              ? 'El pago fue registrado correctamente en el sistema. Puedes notificar al doctor por WhatsApp.'
              : 'Confirma el pago de la comisión pendiente al doctor derivador.'}
          </DialogDescription>
        </DialogHeader>

        {liquidadoExitoso ? (
          <div className="flex flex-col gap-4 py-2">
            <div className="flex items-center gap-3 rounded-card border border-primary/20 bg-primary/10 p-4">
              <CheckCircle2 className="size-8 text-primary shrink-0" />
              <div>
                <p className="font-bold text-on-surface">Liquidación S/ {comision.montoComision.toFixed(2)} Registrada</p>
                <p className="text-xs text-on-surface-variant">
                  Método: {metodoPago} • Paciente: {comision.pacienteNombre}
                </p>
              </div>
            </div>

            {telefonoLimpio && (
              <div className="flex flex-col gap-2 rounded-panel border border-outline-variant/60 bg-surface-container-low p-4">
                <span className="text-xs font-bold text-on-surface">Notificar al Doctor por WhatsApp:</span>
                <p className="text-xs text-on-surface-variant">
                  Envía una confirmación inmediata al {comision.doctorDerivadorTelefono}:
                </p>
                <a
                  href={urlWhatsApp}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-1 inline-flex items-center justify-center gap-2 rounded-control bg-secondary px-4 py-2.5 text-sm font-bold text-on-secondary shadow-sm hover:opacity-95"
                >
                  <MessageCircle className="size-4" />
                  Enviar constancia por WhatsApp
                </a>
              </div>
            )}

            <div className="mt-2 flex justify-end">
              <Button
                type="button"
                variant="secundario"
                onClick={() => {
                  setLiquidadoExitoso(false)
                  onCerrar()
                }}
              >
                Cerrar
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleLiquidar} className="flex flex-col gap-4 pt-2">
            {error && (
              <div className="flex items-center gap-2 rounded-panel bg-error-container/20 p-3 text-xs text-error">
                <AlertCircle className="size-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="rounded-panel border border-tertiary/20 bg-tertiary-container/10 p-3 text-xs">
              <div className="flex justify-between py-0.5">
                <span className="text-on-surface-variant">Doctor Destino:</span>
                <span className="font-bold text-on-surface">{comision.doctorDerivadorNombre}</span>
              </div>
              <div className="flex justify-between py-0.5">
                <span className="text-on-surface-variant">Cuenta / Yape:</span>
                <span className="font-mono font-bold text-tertiary">
                  {comision.doctorDerivadorCuenta ?? 'No registrada'}
                </span>
              </div>
              <div className="flex justify-between py-0.5">
                <span className="text-on-surface-variant">Estudio / Paciente:</span>
                <span className="text-on-surface">
                  {comision.tipoEstudio} • {comision.pacienteNombre}
                </span>
              </div>
              <div className="mt-1 flex justify-between border-t border-tertiary/20 pt-1 text-sm font-bold">
                <span className="text-on-surface">Monto a Liquidar:</span>
                <span className="text-tertiary">S/ {comision.montoComision.toFixed(2)}</span>
              </div>
            </div>

            <div className="flex flex-col gap-3">
              <div>
                <label htmlFor={`${uid}-metodo-de-pago`} className="mb-1 block text-xs font-bold text-on-surface">Método de Pago *</label>
                <select
                  id={`${uid}-metodo-de-pago`}
                  value={metodoPago}
                  onChange={(e) => {
                    if (esOpcion(METODOS_LIQUIDACION, e.target.value)) setMetodoPago(e.target.value)
                  }}
                  className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-focus"
                >
                  <option value="YAPE">Yape (Billetera BCP)</option>
                  <option value="PLIN">Plin (Billetera Interbank/BBVA/Scotiabank)</option>
                  <option value="EFECTIVO">Efectivo de la caja</option>
                </select>
              </div>

              <div>
                <label htmlFor={`${uid}-nota-adicional`} className="mb-1 block text-xs font-bold text-on-surface">Nota adicional</label>
                <Input
                  id={`${uid}-nota-adicional`}
                  value={nota}
                  onChange={(e) => setNota(e.target.value)}
                  placeholder="Ej. Liquidado junto con el envío de la radiografía panorámica"
                />
              </div>
            </div>

            <div className="mt-2 flex items-center justify-end gap-2">
              <Button type="button" variant="secundario" onClick={onCerrar} disabled={liquidando}>
                Cancelar
              </Button>
              <Button type="submit" variant="accion" disabled={liquidando}>
                {liquidando ? 'Registrando Pago...' : 'Confirmar y Liquidar Comisión'}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
