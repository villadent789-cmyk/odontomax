import React, { useId, useState } from 'react'
import { AlertCircle, Building2, FlaskConical, Mail, MapPin, Phone, Plus, User } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/design-system/components/Dialog'
import {
  useCrearProveedorMutation,
  useActualizarProveedorMutation,
  useProveedoresLaboratorio,
} from '../api-laboratorio'
import type { GuardarLaboratorioPayload, LaboratorioExterno } from '../types'

interface Props {
  abierto: boolean
  onCerrar: () => void
  puedeEditar?: boolean
}

export function LaboratoriosModal({ abierto, onCerrar, puedeEditar = true }: Props) {
  const uid = useId()
  const { data: laboratorios = [], isLoading } = useProveedoresLaboratorio(false)
  const crearLabMut = useCrearProveedorMutation()
  const actualizarLabMut = useActualizarProveedorMutation()

  const [modoCrear, setModoCrear] = useState(false)
  const [labEditar, setLabEditar] = useState<LaboratorioExterno | null>(null)

  const [nombre, setNombre] = useState('')
  const [contacto, setContacto] = useState('')
  const [telefono, setTelefono] = useState('')
  const [email, setEmail] = useState('')
  const [direccion, setDireccion] = useState('')
  const [diasPromedio, setDiasPromedio] = useState('5')
  const [activo, setActivo] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const abrirEdicion = (lab: LaboratorioExterno) => {
    setLabEditar(lab)
    setModoCrear(false)
    setNombre(lab.nombre)
    setContacto(lab.contacto || '')
    setTelefono(lab.telefono || '')
    setEmail(lab.email || '')
    setDireccion(lab.direccion || '')
    setDiasPromedio(String(lab.tiempoEntregaPromedioDias || 5))
    setActivo(lab.activo)
    setErrorMsg('')
  }

  const abrirNuevo = () => {
    setLabEditar(null)
    setModoCrear(true)
    setNombre('')
    setContacto('')
    setTelefono('')
    setEmail('')
    setDireccion('')
    setDiasPromedio('5')
    setActivo(true)
    setErrorMsg('')
  }

  const cancelarFormulario = () => {
    setModoCrear(false)
    setLabEditar(null)
    setErrorMsg('')
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nombre.trim()) {
      setErrorMsg('El nombre del laboratorio dental es obligatorio.')
      return
    }

    const payload: GuardarLaboratorioPayload = {
      nombre: nombre.trim(),
      contacto: contacto.trim() || undefined,
      telefono: telefono.trim() || undefined,
      email: email.trim() || undefined,
      direccion: direccion.trim() || undefined,
      tiempoEntregaPromedioDias: parseInt(diasPromedio, 10) || 5,
      activo,
    }

    try {
      if (labEditar) {
        await actualizarLabMut.mutateAsync({ id: labEditar.id, payload })
      } else {
        await crearLabMut.mutateAsync(payload)
      }
      cancelarFormulario()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al guardar laboratorio'
      setErrorMsg(msg)
    }
  }

  const guardando = crearLabMut.isPending || actualizarLabMut.isPending

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-amber-50 border border-amber-200 flex items-center justify-center text-amber-700">
                <Building2 className="w-4 h-4" />
              </div>
              <div>
                <DialogTitle>Directorio de Laboratorios Dentales Externos</DialogTitle>
                <DialogDescription>
                  Talleres protesistas, técnicos y centros CAD/CAM colaboradores.
                </DialogDescription>
              </div>
            </div>

            {puedeEditar && !modoCrear && !labEditar && (
              <Button size="sm" variant="primario" onClick={abrirNuevo}>
                <Plus className="w-3.5 h-3.5 mr-1" />
                Nuevo Laboratorio
              </Button>
            )}
          </div>
        </DialogHeader>

        {/* Formulario de Alta / Edición */}
        {(modoCrear || labEditar) && (
          <form
            onSubmit={handleSubmit}
            className="p-4 bg-slate-50 rounded-2xl border border-slate-200 space-y-3 mb-2"
          >
            <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
              {labEditar ? `Editar ${labEditar.nombre}` : 'Registrar Nuevo Proveedor de Laboratorio'}
            </h4>

            {errorMsg && (
              <div className="p-2 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-xs flex items-center gap-1.5">
                <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-nombre-del-laboratorio`} className="font-semibold text-slate-700">Nombre del Laboratorio *</label>
                <input
                  id={`${uid}-nombre-del-laboratorio`}
                  type="text"
                  required
                  placeholder="Ej. Lab Dental Huancayo SAC"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-contacto-tecnico-responsable`} className="font-semibold text-slate-700">Contacto / Técnico Responsable</label>
                <input
                  id={`${uid}-contacto-tecnico-responsable`}
                  type="text"
                  placeholder="Ej. Téc. Juan Pérez"
                  value={contacto}
                  onChange={(e) => setContacto(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-telefono-celular`} className="font-semibold text-slate-700">Teléfono / Celular</label>
                <input
                  id={`${uid}-telefono-celular`}
                  type="text"
                  placeholder="Ej. 964 123 456"
                  value={telefono}
                  onChange={(e) => setTelefono(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-correo-electronico`} className="font-semibold text-slate-700">Correo Electrónico</label>
                <input
                  id={`${uid}-correo-electronico`}
                  type="email"
                  placeholder="lab@ejemplo.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-direccion-ubicacion`} className="font-semibold text-slate-700">Dirección / Ubicación</label>
                <input
                  id={`${uid}-direccion-ubicacion`}
                  type="text"
                  placeholder="Ej. Jr. Puno 340, Huancayo"
                  value={direccion}
                  onChange={(e) => setDireccion(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={`${uid}-tiempo-entrega-promedio-dias`} className="font-semibold text-slate-700">Tiempo Entrega Promedio (Días)</label>
                <input
                  id={`${uid}-tiempo-entrega-promedio-dias`}
                  type="number"
                  min="1"
                  max="30"
                  value={diasPromedio}
                  onChange={(e) => setDiasPromedio(e.target.value)}
                  className="h-9 px-3 rounded-xl border border-slate-300 bg-white"
                />
              </div>
            </div>

            <div className="flex items-center justify-between pt-1">
              <label className="text-xs font-semibold text-slate-700 flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={activo}
                  onChange={(e) => setActivo(e.target.checked)}
                  className="rounded text-indigo-600"
                />
                Laboratorio Activo para Nuevas Órdenes
              </label>

              <div className="flex gap-2">
                <Button size="sm" variant="secundario" type="button" onClick={cancelarFormulario} disabled={guardando}>
                  Cancelar
                </Button>
                <Button size="sm" variant="primario" type="submit" disabled={guardando}>
                  {guardando ? 'Guardando...' : labEditar ? 'Guardar Cambios' : 'Registrar'}
                </Button>
              </div>
            </div>
          </form>
        )}

        {/* Lista de Laboratorios */}
        <div className="space-y-2.5 pt-1">
          {isLoading ? (
            <div className="py-8 text-center text-xs text-slate-500">Cargando directorio...</div>
          ) : laboratorios.length === 0 ? (
            <div className="py-8 text-center text-xs text-slate-500">No hay laboratorios registrados.</div>
          ) : (
            laboratorios.map((lab) => (
              <div
                key={lab.id}
                className="p-3.5 bg-white rounded-xl border border-slate-200 hover:border-slate-300 shadow-sm transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <FlaskConical className="w-4 h-4 text-amber-600" />
                    <strong className="text-sm text-slate-900">{lab.nombre}</strong>
                    {!lab.activo && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-500 font-semibold border border-slate-200">
                        Inactivo
                      </span>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
                    {lab.contacto && (
                      <span className="flex items-center gap-1">
                        <User className="w-3.5 h-3.5 text-slate-400" />
                        {lab.contacto}
                      </span>
                    )}
                    {lab.telefono && (
                      <span className="flex items-center gap-1">
                        <Phone className="w-3.5 h-3.5 text-slate-400" />
                        {lab.telefono}
                      </span>
                    )}
                    {lab.email && (
                      <span className="flex items-center gap-1">
                        <Mail className="w-3.5 h-3.5 text-slate-400" />
                        {lab.email}
                      </span>
                    )}
                    {lab.direccion && (
                      <span className="flex items-center gap-1">
                        <MapPin className="w-3.5 h-3.5 text-slate-400" />
                        {lab.direccion}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex items-center gap-3 self-end sm:self-center">
                  <span className="text-xs bg-slate-50 px-2.5 py-1 rounded-lg border border-slate-200 text-slate-700 whitespace-nowrap">
                    Entrega: <strong>{lab.tiempoEntregaPromedioDias} días</strong>
                  </span>
                  {puedeEditar && (
                    <Button size="sm" variant="secundario" onClick={() => abrirEdicion(lab)}>
                      Editar
                    </Button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
