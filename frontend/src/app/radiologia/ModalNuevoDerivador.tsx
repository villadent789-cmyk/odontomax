import { useState, useId } from 'react'
import { AlertCircle } from 'lucide-react'
import { Button, Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, Input } from '@/design-system'
import type { DoctorDerivadorDTO, DoctorDerivadorRequestDTO } from './types'

interface ModalNuevoDerivadorProps {
  abierto: boolean
  onCerrar: () => void
  onGuardar: (req: DoctorDerivadorRequestDTO) => Promise<void>
  doctorAEditar?: DoctorDerivadorDTO | null
}

export function ModalNuevoDerivador({
  abierto,
  onCerrar,
  onGuardar,
  doctorAEditar,
}: ModalNuevoDerivadorProps) {
  const uid = useId()
  const [nombre, setNombre] = useState(doctorAEditar?.nombre ?? '')
  const [cop, setCop] = useState(doctorAEditar?.cop ?? '')
  const [clinicaOrigen, setClinicaOrigen] = useState(doctorAEditar?.clinicaOrigen ?? '')
  const [telefono, setTelefono] = useState(doctorAEditar?.telefono ?? '')
  const [banco, setBanco] = useState(doctorAEditar?.banco ?? 'BCP / Yape')
  const [numeroCuentaOYape, setNumeroCuentaOYape] = useState(doctorAEditar?.numeroCuentaOYape ?? '')
  const [notas, setNotas] = useState(doctorAEditar?.notas ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nombre.trim()) {
      setError('El nombre del doctor es obligatorio')
      return
    }

    try {
      setGuardando(true)
      setError(null)
      await onGuardar({
        nombre: nombre.trim(),
        cop: cop.trim() || undefined,
        clinicaOrigen: clinicaOrigen.trim() || undefined,
        telefono: telefono.trim() || undefined,
        banco: banco.trim() || undefined,
        numeroCuentaOYape: numeroCuentaOYape.trim() || undefined,
        activo: true,
        notas: notas.trim() || undefined,
      })
      onCerrar()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al guardar el doctor derivador')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{doctorAEditar ? 'Editar Doctor Derivador' : 'Registrar Doctor Derivador'}</DialogTitle>
          <DialogDescription>
            Afilia o actualiza a un odontólogo / clínica externa que deriva pacientes a Villamax. La comisión de cada estudio es fija y se define en Administración → Servicios.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 pt-2">
          {error && (
            <div className="flex items-center gap-2 rounded-panel bg-error-container/20 p-3 text-xs text-error">
              <AlertCircle className="size-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label htmlFor={`${uid}-nombre-completo`} className="mb-1 block text-xs font-bold text-on-surface">Nombre Completo *</label>
              <Input
                id={`${uid}-nombre-completo`}
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Ej. Dr. Carlos Palomino Quispe"
                required
              />
            </div>

            <div>
              <label htmlFor={`${uid}-n-cop-colegio-odontologico`} className="mb-1 block text-xs font-bold text-on-surface">N.° COP (Colegio Odontológico)</label>
              <Input
                id={`${uid}-n-cop-colegio-odontologico`}
                value={cop}
                onChange={(e) => setCop(e.target.value)}
                placeholder="Ej. COP 28410"
              />
            </div>

            <div>
              <label htmlFor={`${uid}-clinica-procedencia`} className="mb-1 block text-xs font-bold text-on-surface">Clínica / Procedencia</label>
              <Input
                id={`${uid}-clinica-procedencia`}
                value={clinicaOrigen}
                onChange={(e) => setClinicaOrigen(e.target.value)}
                placeholder="Ej. Dental Sonrisas (El Tambo)"
              />
            </div>

            <div>
              <label htmlFor={`${uid}-whatsapp-celular`} className="mb-1 block text-xs font-bold text-on-surface">WhatsApp / Celular</label>
              <Input
                id={`${uid}-whatsapp-celular`}
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                placeholder="Ej. 964123456"
              />
            </div>

            <div>
              <label htmlFor={`${uid}-banco-o-billetera`} className="mb-1 block text-xs font-bold text-on-surface">Banco o Billetera</label>
              <Input
                id={`${uid}-banco-o-billetera`}
                value={banco}
                onChange={(e) => setBanco(e.target.value)}
                placeholder="Ej. Yape / BCP, Plin, BBVA"
              />
            </div>

            <div>
              <label htmlFor={`${uid}-n-celular-yape-o`} className="mb-1 block text-xs font-bold text-on-surface">N.° Celular Yape o Cuenta</label>
              <Input
                id={`${uid}-n-celular-yape-o`}
                value={numeroCuentaOYape}
                onChange={(e) => setNumeroCuentaOYape(e.target.value)}
                placeholder="Ej. 964123456"
              />
            </div>

            <div className="sm:col-span-2">
              <label htmlFor={`${uid}-notas-convenio`} className="mb-1 block text-xs font-bold text-on-surface">Notas / Convenio</label>
              <Input
                id={`${uid}-notas-convenio`}
                value={notas}
                onChange={(e) => setNotas(e.target.value)}
                placeholder="Ej. Sello físico verificado, enviar RX por WhatsApp"
              />
            </div>
          </div>

          <div className="mt-2 flex items-center justify-end gap-2">
            <Button type="button" variant="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={guardando}>
              {guardando ? 'Guardando...' : doctorAEditar ? 'Guardar Cambios' : 'Registrar Doctor'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
