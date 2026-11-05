import { METODOS_PAGO, type MetodoPago } from '@/shared/metodosPago'
import { useState } from 'react'
import { Alert, Button } from '@/design-system'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { useCajaCerrada } from '../caja/useCajaCerrada'
import { useRegistrarPagoMutation } from '../caja/api-caja'
import { crearNotaEvolucion } from '../clinica/api-historia'
import { useCambiarEstado, type Cita } from './api-agenda'
import { useSesion } from '@/shared/auth'
import { useServiciosCatalogo, useTratamiento } from '../tratamientos/api-tratamientos'
import { textoDeError } from '@/shared/http'

interface Props {
  cita: Cita
  abierto: boolean
  alCerrar: () => void
  onActualizada: (cita: Cita) => void
}

/**
 * Flujo rápido "Finalizar Atención & Cobro".
 * Con Villamax: formulario simplificado (sin notas clínicas, solo monto precargado y método de pago).
 * Con Villadent: marca ATENDIDA + nota SOAP rápida (opcional) + registra cobro (opcional).
 */
export function FinalizarAtencionModal({ cita, abierto, alCerrar, onActualizada }: Props) {
  const cambiarEstado = useCambiarEstado()
  const registrarPago = useRegistrarPagoMutation()
  const sesion = useSesion()
  const esVillamax = sesion?.negocioActivo === 'VILLAMAX'

  const { data: tratamiento } = useTratamiento(cita.tratamientoId ?? undefined)
  const servicios = useServiciosCatalogo(cita.sedeId, abierto && esVillamax, sesion?.negocioActivo)

  const [subjetivo, setSubjetivo] = useState('')
  const [plan, setPlan] = useState('')
  const [montoCustom, setMontoCustom] = useState<string | null>(null)
  const [metodoPago, setMetodoPago] = useState<MetodoPago>('EFECTIVO')
  const [error, setError] = useState<string | null>(null)

  // Monto sugerido precargado en Villamax con el total a cobrar pendiente
  const montoSugerido = (() => {
    if (!esVillamax) return ''
    if (tratamiento?.saldoPendiente != null) {
      return tratamiento.saldoPendiente > 0 ? tratamiento.saldoPendiente.toFixed(2) : '0.00'
    }
    const srv = (servicios.data ?? []).find((s) => s.nombre === cita.motivoConsulta)
    if (srv?.precio != null) {
      const saldo = Math.max(0, Number(srv.precio) - (Number(cita.adelantoMonto) || 0))
      return saldo > 0 ? saldo.toFixed(2) : '0.00'
    }
    return ''
  })()

  const monto = montoCustom ?? montoSugerido

  const enviando = cambiarEstado.isPending || registrarPago.isPending
  // Se consulta la caja de la sede de la cita (donde se registrará el cobro), no la sede activa de la cabecera.
  const cajaCerrada = useCajaCerrada(abierto ? cita.sedeId : null)

  function cerrarModal() {
    setMontoCustom(null)
    setSubjetivo('')
    setPlan('')
    setError(null)
    alCerrar()
  }

  async function manejarEnvio(e: React.FormEvent) {
    e.preventDefault()
    setError(null)

    const montoNum = parseFloat(monto)
    if (monto && (isNaN(montoNum) || montoNum <= 0)) {
      setError('El monto debe ser un número positivo')
      return
    }

    try {
      // 1. Cobro (solo si el monto es positivo). Va primero: es lo que más puede fallar (p. ej. caja cerrada) y,
      //    si falla, no queda a medias una nota que un reintento duplicaría.
      if (monto && montoNum > 0 && cita.pacienteId && cita.sedeId) {
        await registrarPago.mutateAsync({
          pacienteId: cita.pacienteId,
          tratamientoId: cita.tratamientoId ?? undefined,
          sedeId: cita.sedeId,
          monto: montoNum,
          metodoPago,
        })
      }

      // 2. Nota SOAP rápida (solo si no es Villamax y hay contenido)
      if (!esVillamax) {
        const hayNota = subjetivo.trim() || plan.trim()
        if (hayNota && cita.pacienteId) {
          await crearNotaEvolucion(cita.pacienteId, {
            citaId: cita.id,
            tipoNota: 'SOAP',
            subjetivo: subjetivo.trim() || undefined,
            plan: plan.trim() || undefined,
          })
        }
      }

      // 3. Marcar ATENDIDA (siempre)
      const actualizada = await cambiarEstado.mutateAsync({ id: cita.id!, estado: 'ATENDIDA' })
      if (actualizada) onActualizada(actualizada)

      cerrarModal()
    } catch (err) {
      setError(textoDeError(err, 'Error al finalizar la atención'))
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && cerrarModal()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Finalizar Atención y Cobro</DialogTitle>
          <DialogDescription>
            {cita.pacienteNombre ?? 'Paciente'} — {cita.motivoConsulta ?? 'Consulta'}
          </DialogDescription>
        </DialogHeader>

        {error && <Alert tono="error" titulo={error} />}

        <form onSubmit={manejarEnvio} className="space-y-4 pt-1">
          {!esVillamax && (
            <section className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wider text-primary">
                Nota clínica rápida (opcional)
              </p>
              <Field label="Motivo / Queja del paciente (S)">
                <Input
                  id="finalizarSubjetivo"
                  value={subjetivo}
                  onChange={(e) => setSubjetivo(e.target.value)}
                  placeholder="Ej. Dolor molar superior derecho desde 3 días"
                />
              </Field>
              <Field label="Procedimiento realizado / Plan (P)">
                <Input
                  id="finalizarPlan"
                  value={plan}
                  onChange={(e) => setPlan(e.target.value)}
                  placeholder="Ej. Curación con resina pieza 16, cara oclusal"
                />
              </Field>
            </section>
          )}

          <section className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-primary">
              {esVillamax ? 'Cobro del estudio' : 'Cobro en sillón (opcional)'}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Monto (S/)">
                <Input
                  id="finalizarMonto"
                  type="number"
                  step="0.01"
                  min="0"
                  value={monto}
                  onChange={(e) => setMontoCustom(e.target.value)}
                  placeholder="0.00"
                />
              </Field>
              <Field label="Método de pago">
                <select
                  id="finalizarMetodoPago"
                  value={metodoPago}
                  onChange={(e) => setMetodoPago(e.target.value as MetodoPago)}
                  className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  {METODOS_PAGO.map((m) => (<option key={m.valor} value={m.valor}>{m.nombre}</option>))}
                </select>
              </Field>
            </div>
            {cajaCerrada && monto.trim() !== '' && (
              <Alert
                tono="aviso"
                titulo="Aviso: La caja de la sede no está abierta. Se abrirá automáticamente con saldo inicial S/ 0.00 al registrar el cobro."
              />
            )}
          </section>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-outline">
            <Button type="button" variant="secundario" onClick={cerrarModal} disabled={enviando}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={enviando} cargando={enviando}>
              {enviando ? 'Finalizando...' : 'Finalizar Atención'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
