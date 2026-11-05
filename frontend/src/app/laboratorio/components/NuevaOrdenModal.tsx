import React, { useState } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { AlertCircle, FlaskConical, Calendar, DollarSign, Stethoscope } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/design-system/components/Dialog'
import { BuscadorPaciente } from '../../agenda/BuscadorPaciente'
import { useEspecialistas, useSedesActivas, type Paciente } from '../../agenda/api-agenda'
import {
  useCrearOrdenMutation,
  useActualizarOrdenMutation,
  useProveedoresLaboratorio,
} from '../api-laboratorio'
import type { OrdenLaboratorio } from '../types'

interface Props {
  abierto: boolean
  onCerrar: () => void
  ordenEditar?: OrdenLaboratorio | null
  sedeIdInicial?: string
  esOdontologo?: boolean
}

const CLASE_CAMPO =
  'h-9 px-3 rounded-xl border border-slate-200 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500'
const CLASE_AREA =
  'p-2.5 rounded-xl border border-slate-200 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500'

const TRABAJOS_COMUNES = [
  'Corona Zirconio Monolítico',
  'Corona Metal-Porcelana',
  'Corona Disilicato de Litio (E-Max)',
  'Incrustación Inlay/Onlay Cerámica',
  'Carilla Estética Porcelana',
  'Perno Muñón Colado',
  'Prótesis Total Removible (Acrílico Lucitone)',
  'Prótesis Parcial Removible (Cromo-Cobalto)',
  'Prótesis Flexible (Valplast / Deflex)',
  'Placa Miorrelajante Rígida (Bruxismo)',
  'Retenedor Essix Transparente',
  'Corona sobre Implante Atornillada',
  'Póntico de 3 Unidades Zirconio',
]

const MATERIALES_COMUNES = [
  'Zirconio Monolítico',
  'Disilicato de Litio (E-Max)',
  'Metal-Porcelana',
  'Acrílico Termocurable',
  'Acrílico Autocurable',
  'Cromo-Cobalto',
  'Resina PMMA',
  'Peek Odontológico',
  'Flexible (Valplast / Deflex)',
]

export function NuevaOrdenModal({ abierto, onCerrar, ordenEditar, sedeIdInicial, esOdontologo = false }: Props) {
  const esEdicion = Boolean(ordenEditar)

  const { data: sedes = [] } = useSedesActivas()
  const { data: laboratorios = [] } = useProveedoresLaboratorio(true)

  const [sedeId, setSedeId] = useState(sedeIdInicial || (sedes[0]?.id ?? 'sede-chilca-1'))
  const [paciente, setPaciente] = useState<Paciente | null>(null)
  const [especialistaId, setEspecialistaId] = useState('')
  const [laboratorioId, setLaboratorioId] = useState('')
  const [tipoTrabajo, setTipoTrabajo] = useState('')
  const [colorTono, setColorTono] = useState('')
  const [material, setMaterial] = useState('')
  const [provisional, setProvisional] = useState(false)
  const [piezasDentales, setPiezasDentales] = useState('')
  const [fechaEstimadaEntrega, setFechaEstimadaEntrega] = useState('')
  const [costoLaboratorio, setCostoLaboratorio] = useState('')
  const [precioPaciente, setPrecioPaciente] = useState('')
  const [guiaRemision, setGuiaRemision] = useState('')
  const [indicacionesTecnicas, setIndicacionesTecnicas] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [errorGeneral, setErrorGeneral] = useState('')

  const { data: especialistas = [] } = useEspecialistas(sedeId)
  const crearOrdenMut = useCrearOrdenMutation()
  const actualizarOrdenMut = useActualizarOrdenMutation()

  const primerLaboratorio = laboratorios[0]
  useAlCambiar(
    [abierto, ordenEditar, sedeIdInicial, primerLaboratorio?.id, primerLaboratorio?.tiempoEntregaPromedioDias, sedes[0]?.id],
    () => {
    if (abierto) {
      setErrorGeneral('')
      if (ordenEditar) {
        setSedeId(ordenEditar.sedeId || (sedes[0]?.id ?? 'sede-chilca-1'))
        setPaciente({
          id: ordenEditar.pacienteId,
          nombres: ordenEditar.pacienteNombre,
          apellidos: '',
          dni: ordenEditar.pacienteDni ?? '',
          sedeId: ordenEditar.sedeId,
        })
        setEspecialistaId(ordenEditar.especialistaId || '')
        setLaboratorioId(ordenEditar.laboratorioId || '')
        setTipoTrabajo(ordenEditar.tipoTrabajo || '')
        setColorTono(ordenEditar.colorTono || ordenEditar.color || '')
        setMaterial(ordenEditar.material || '')
        setProvisional(Boolean(ordenEditar.provisional))
        setPiezasDentales(ordenEditar.piezasDentales || '')
        setFechaEstimadaEntrega(ordenEditar.fechaEstimadaEntrega || '')
        setCostoLaboratorio(ordenEditar.costoLaboratorio ? String(ordenEditar.costoLaboratorio) : '')
        setPrecioPaciente(ordenEditar.precioPaciente ? String(ordenEditar.precioPaciente) : '')
        setGuiaRemision(ordenEditar.guiaRemision || '')
        setIndicacionesTecnicas(ordenEditar.indicacionesTecnicas || '')
        setObservaciones(ordenEditar.observaciones || '')
      } else {
        setSedeId(sedeIdInicial || (sedes[0]?.id ?? 'sede-chilca-1'))
        setPaciente(null)
        setEspecialistaId('')
        setLaboratorioId(laboratorios[0]?.id ?? '')
        setTipoTrabajo('')
        setColorTono('')
        setMaterial('')
        setProvisional(false)
        setPiezasDentales('')
        setCostoLaboratorio('')
        setPrecioPaciente('')
        setGuiaRemision('')
        setIndicacionesTecnicas('')
        setObservaciones('')

        // Calcular fecha estimada por defecto (hoy + días del lab)
        const diasPromedio = laboratorios[0]?.tiempoEntregaPromedioDias || 5
        const fecha = new Date()
        fecha.setDate(fecha.getDate() + diasPromedio)
        setFechaEstimadaEntrega(fecha.toISOString().slice(0, 10))
      }
    }
    },
  )

  // Actualizar fecha estimada sugerida si cambia el laboratorio en modo creación
  const handleLaboratorioChange = (nuevoLabId: string) => {
    setLaboratorioId(nuevoLabId)
    if (!esEdicion) {
      const lab = laboratorios.find((l) => l.id === nuevoLabId)
      const dias = lab?.tiempoEntregaPromedioDias || 5
      const fecha = new Date()
      fecha.setDate(fecha.getDate() + dias)
      setFechaEstimadaEntrega(fecha.toISOString().slice(0, 10))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorGeneral('')

    if (!esEdicion && !paciente) {
      setErrorGeneral('Debe seleccionar o registrar al paciente para esta orden.')
      return
    }
    if (!esEdicion && !especialistaId) {
      setErrorGeneral('Debe seleccionar el odontólogo tratante.')
      return
    }
    if (!laboratorioId) {
      setErrorGeneral('Debe seleccionar el laboratorio dental externo.')
      return
    }
    if (!tipoTrabajo.trim()) {
      setErrorGeneral('Debe ingresar el tipo de prótesis o trabajo protésico.')
      return
    }

    try {
      if (esEdicion && ordenEditar) {
        await actualizarOrdenMut.mutateAsync({
          id: ordenEditar.id,
          payload: {
            laboratorioId,
            tipoTrabajo: tipoTrabajo.trim(),
            colorTono: colorTono.trim() || undefined,
            color: colorTono.trim() || undefined,
            material: material.trim() || undefined,
            provisional,
            piezasDentales: piezasDentales.trim() || undefined,
            fechaEstimadaEntrega: fechaEstimadaEntrega || undefined,
            costoLaboratorio: costoLaboratorio ? parseFloat(costoLaboratorio) : undefined,
            precioPaciente: precioPaciente ? parseFloat(precioPaciente) : undefined,
            guiaRemision: guiaRemision.trim() || undefined,
            indicacionesTecnicas: indicacionesTecnicas.trim() || undefined,
            observaciones: observaciones.trim() || undefined,
          },
        })
      } else {
        await crearOrdenMut.mutateAsync({
          pacienteId: paciente!.id!,
          especialistaId,
          sedeId,
          laboratorioId,
          tipoTrabajo: tipoTrabajo.trim(),
          colorTono: colorTono.trim() || undefined,
          color: colorTono.trim() || undefined,
          material: material.trim() || undefined,
          provisional,
          piezasDentales: piezasDentales.trim() || undefined,
          fechaEstimadaEntrega: fechaEstimadaEntrega || undefined,
          costoLaboratorio: costoLaboratorio ? parseFloat(costoLaboratorio) : undefined,
          precioPaciente: precioPaciente ? parseFloat(precioPaciente) : undefined,
          guiaRemision: guiaRemision.trim() || undefined,
          indicacionesTecnicas: indicacionesTecnicas.trim() || undefined,
          observaciones: observaciones.trim() || undefined,
        })
      }
      onCerrar()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al guardar la orden de laboratorio'
      setErrorGeneral(msg)
    }
  }

  const cargando = crearOrdenMut.isPending || actualizarOrdenMut.isPending
  // Al editar una orden que ya tiene datos secundarios, "Más datos" se muestra abierto.
  const tieneDatosExtra = [piezasDentales, ...(esOdontologo ? [] : [precioPaciente]), guiaRemision, indicacionesTecnicas, observaciones].some((v) => v.trim() !== '')

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
      <DialogContent className="max-w-2xl max-h-[92vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-700">
              <FlaskConical className="w-4 h-4" />
            </div>
            <div>
              <DialogTitle>
                {esEdicion ? `Editar Orden Protésica (${ordenEditar?.codigo})` : 'Nueva Orden de Laboratorio Dental'}
              </DialogTitle>
              <DialogDescription>
                {esEdicion
                  ? 'Actualice el trabajo, el laboratorio, el costo o la fecha de entrega de la orden.'
                  : 'Registre una orden de prótesis o aparatología para envío a laboratorio externo.'}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {errorGeneral && (
          <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
            <span>{errorGeneral}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 pt-1">
          {/* Sede y Paciente */}
          {!esEdicion && (
            <div className="space-y-3 p-3.5 bg-slate-50/70 border border-slate-200/80 rounded-xl">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="orden-sede" className="text-xs font-semibold text-slate-700">
                    Sede Clínica <span className="text-rose-500">*</span>
                  </label>
                  <select
                    id="orden-sede"
                    value={sedeId}
                    onChange={(e) => setSedeId(e.target.value)}
                    className="h-9 px-3 rounded-xl border border-slate-200 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  >
                    {sedes.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.nombre}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label htmlFor="orden-odontologo" className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                    <Stethoscope className="w-3.5 h-3.5 text-indigo-600" />
                    Odontólogo Tratante <span className="text-rose-500">*</span>
                  </label>
                  <select
                    id="orden-odontologo"
                    value={especialistaId}
                    onChange={(e) => setEspecialistaId(e.target.value)}
                    required
                    className="h-9 px-3 rounded-xl border border-slate-200 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  >
                    <option value="">Seleccione especialista...</option>
                    {especialistas.map((esp) => (
                      <option key={esp.id} value={esp.id}>
                        {esp.nombre} {esp.cop ? `(COP ${esp.cop})` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="pt-1">
                {/* El buscador trae su propio campo "Paciente" con etiqueta asociada; este rótulo solo marca que es obligatorio. */}
                <p aria-hidden="true" className="text-xs font-semibold text-slate-700 block mb-1.5">
                  Paciente <span className="text-rose-500">*</span>
                </p>
                <BuscadorPaciente
                  sedeId={sedeId}
                  seleccionado={paciente}
                  onSeleccionar={setPaciente}
                />
              </div>
            </div>
          )}

          {/* Lo esencial: trabajo, laboratorio, costo para la clínica y fecha de entrega */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="orden-trabajo" className="text-xs font-semibold text-slate-700">
                Tratamiento / Trabajo protésico <span className="text-rose-500">*</span>
              </label>
              <input
                id="orden-trabajo"
                type="text"
                list="lista-trabajos-lab"
                placeholder="Ej. Corona Zirconio, Prótesis total, Perno..."
                value={tipoTrabajo}
                onChange={(e) => setTipoTrabajo(e.target.value)}
                required
                className={CLASE_CAMPO}
              />
              <datalist id="lista-trabajos-lab">
                {TRABAJOS_COMUNES.map((t) => (
                  <option key={t} value={t} />
                ))}
              </datalist>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="orden-laboratorio" className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                <FlaskConical className="w-3.5 h-3.5 text-amber-600" />
                Laboratorio externo <span className="text-rose-500">*</span>
              </label>
              <select
                id="orden-laboratorio"
                value={laboratorioId}
                onChange={(e) => handleLaboratorioChange(e.target.value)}
                required
                className={CLASE_CAMPO}
              >
                <option value="">Seleccione laboratorio...</option>
                {laboratorios.map((lab) => (
                  <option key={lab.id} value={lab.id}>
                    {lab.nombre} (Entrega aprox. {lab.tiempoEntregaPromedioDias} días)
                  </option>
                ))}
              </select>
            </div>

            {!esOdontologo && (
              <div className="flex flex-col gap-1.5">
                <label htmlFor="orden-costo" className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                  <DollarSign className="w-3.5 h-3.5 text-rose-600" />
                  Costo que nos cobra el laboratorio (S/)
                </label>
                <input
                  id="orden-costo"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                  value={costoLaboratorio}
                  onChange={(e) => setCostoLaboratorio(e.target.value)}
                  aria-describedby="orden-costo-ayuda"
                  className={CLASE_CAMPO}
                />
                <p id="orden-costo-ayuda" className="text-xs text-slate-500">
                  Gasto directo de la clínica con el taller.
                </p>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label htmlFor="orden-fecha" className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5 text-sky-600" />
                Fecha estimada de entrega
              </label>
              <input
                id="orden-fecha"
                type="date"
                value={fechaEstimadaEntrega}
                onChange={(e) => setFechaEstimadaEntrega(e.target.value)}
                className={CLASE_CAMPO}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="orden-material" className="text-xs font-semibold text-slate-700">
                Material de confección
              </label>
              <input
                id="orden-material"
                type="text"
                list="lista-materiales-lab"
                placeholder="Ej. Zirconio Monolítico, Disilicato..."
                value={material}
                onChange={(e) => setMaterial(e.target.value)}
                className={CLASE_CAMPO}
              />
              <datalist id="lista-materiales-lab">
                {MATERIALES_COMUNES.map((m) => (
                  <option key={m} value={m} />
                ))}
              </datalist>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="orden-color" className="text-xs font-semibold text-slate-700">
                Color / Tono dental (opcional, ej. A2)
              </label>
              <input
                id="orden-color"
                type="text"
                maxLength={20}
                placeholder="Ej. A2, 1M1 (Guía VITA)..."
                value={colorTono}
                onChange={(e) => setColorTono(e.target.value)}
                className={CLASE_CAMPO}
              />
            </div>

            <div className="sm:col-span-2 flex items-center gap-2 pt-1">
              <input
                id="orden-provisional"
                type="checkbox"
                checked={provisional}
                onChange={(e) => setProvisional(e.target.checked)}
                className="w-4 h-4 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
              />
              <label htmlFor="orden-provisional" className="text-xs font-medium text-slate-800 cursor-pointer">
                Prótesis provisional / temporal (dispositivo provisional previo a la restauración definitiva)
              </label>
            </div>
          </div>

          <p className="rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
            El seguimiento (enviado al laboratorio, recibido en clínica, instalado) se marca desde el detalle de la orden.
          </p>

          {/* Datos secundarios, plegados para no recargar el formulario */}
          <details className="rounded-xl border border-slate-200 p-3" open={tieneDatosExtra}>
            <summary className="cursor-pointer text-xs font-semibold text-slate-700">Más datos (opcional)</summary>
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <label htmlFor="orden-piezas" className="text-xs font-semibold text-slate-700">
                  Piezas dentales (FDI)
                </label>
                <input
                  id="orden-piezas"
                  type="text"
                  placeholder="Ej. 11, 21"
                  value={piezasDentales}
                  onChange={(e) => setPiezasDentales(e.target.value)}
                  className={CLASE_CAMPO}
                />
              </div>
              {!esOdontologo && (
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="orden-precio" className="text-xs font-semibold text-slate-700">
                    Precio al paciente (S/)
                  </label>
                  <input
                    id="orden-precio"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="0.00"
                    value={precioPaciente}
                    onChange={(e) => setPrecioPaciente(e.target.value)}
                    className={CLASE_CAMPO}
                  />
                </div>
              )}
              <div className="flex flex-col gap-1.5">
                <label htmlFor="orden-guia" className="text-xs font-semibold text-slate-700">
                  Guía de remisión
                </label>
                <input
                  id="orden-guia"
                  type="text"
                  placeholder="Ej. T-004-9812"
                  value={guiaRemision}
                  onChange={(e) => setGuiaRemision(e.target.value)}
                  className={CLASE_CAMPO}
                />
              </div>
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <label htmlFor="orden-indicaciones" className="text-xs font-semibold text-slate-700">
                  Instrucciones para el laboratorio
                </label>
                <textarea
                  id="orden-indicaciones"
                  rows={2}
                  value={indicacionesTecnicas}
                  onChange={(e) => setIndicacionesTecnicas(e.target.value)}
                  className={CLASE_AREA}
                />
              </div>
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <label htmlFor="orden-observaciones" className="text-xs font-semibold text-slate-700">
                  Observaciones internas
                </label>
                <textarea
                  id="orden-observaciones"
                  rows={1}
                  value={observaciones}
                  onChange={(e) => setObservaciones(e.target.value)}
                  className={CLASE_AREA}
                />
              </div>
            </div>
          </details>

          <DialogFooter className="pt-2">
            <Button variant="secundario" type="button" onClick={onCerrar} disabled={cargando}>
              Cancelar
            </Button>
            <Button variant="primario" type="submit" disabled={cargando}>
              {cargando ? 'Guardando...' : esEdicion ? 'Actualizar Orden' : 'Emitir Orden a Laboratorio'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
