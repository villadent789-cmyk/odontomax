import { useState } from 'react'
import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Field, Input } from '@/design-system'
import { hoyEnLima } from '@/shared/format'
import { api, textoDeError } from '@/shared/http'
import { imprimirDocumento } from '@/shared/imprimir'
import { documentoEstadoCuenta, type EstadoCuenta } from './documentoEstadoCuenta'

interface Props {
  abierto: boolean
  doctor: { id: string; nombre: string } | null
  onCerrar: () => void
}

export function EstadoCuentaDerivador({ abierto, doctor, onCerrar }: Props) {
  const hoy = hoyEnLima()
  const [desde, setDesde] = useState(`${hoy.slice(0, 8)}01`)
  const [hasta, setHasta] = useState(hoy)
  const [error, setError] = useState<string | null>(null)
  const [cargando, setCargando] = useState(false)

  async function imprimir(evento: React.FormEvent) {
    evento.preventDefault()
    if (!doctor) return
    setError(null)
    setCargando(true)
    try {
      const cuenta = await api.get<EstadoCuenta>(
        `/radiologia/comisiones/estado-cuenta?doctorId=${encodeURIComponent(doctor.id)}&desde=${desde}&hasta=${hasta}`,
      )
      await imprimirDocumento(documentoEstadoCuenta(cuenta))
      onCerrar()
    } catch (e) {
      setError(textoDeError(e, 'No se pudo preparar el estado de cuenta.'))
    } finally {
      setCargando(false)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(valor) => !valor && onCerrar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Estado de cuenta</DialogTitle>
          <DialogDescription>{doctor?.nombre}: lo que derivó, lo generado, lo pagado y lo pendiente en el periodo.</DialogDescription>
        </DialogHeader>
        <form onSubmit={imprimir} className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Desde">
              <Input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} required />
            </Field>
            <Field label="Hasta">
              <Input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} required />
            </Field>
          </div>
          {error && (
            <p role="alert" className="text-sm text-error">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="secundario" onClick={onCerrar}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={cargando}>
              {cargando ? 'Preparando…' : 'Imprimir'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
