import React, { useId, useState } from 'react'
import {
  AlertCircle,
  Clock,
  FileText,
  FlaskConical,
  Sparkles,
  Stethoscope,
  User,
} from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import { Badge } from '@/design-system/components/Badge'
import { obtenerSesion } from '@/shared/auth'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/design-system/components/Dialog'
import { EstadoOrdenBadge } from './EstadoOrdenBadge'
import { TimelineStepper } from './TimelineStepper'
import {
  useAnularPagoLaboratorioMutation,
  useCambiarEstadoOrdenMutation,
  useRegistrarPruebaOrdenMutation,
  useOrdenLaboratorio,
  usePagosLaboratorio,
} from '../api-laboratorio'
import { RegistrarPagoLaboratorioModal } from './RegistrarPagoLaboratorioModal'
import type { OrdenLaboratorio, PagoLaboratorio, ResultadoPrueba } from '../types'

interface Props {
  abierto: boolean
  onCerrar: () => void
  ordenId?: string | null
  onEditar?: (orden: OrdenLaboratorio) => void
}

export function DetalleOrdenModal({ abierto, onCerrar, ordenId, onEditar }: Props) {
  const uid = useId()
  const { data: orden, isLoading, error } = useOrdenLaboratorio(ordenId || undefined)
  const cambiarEstadoMut = useCambiarEstadoOrdenMutation()
  const registrarPruebaMut = useRegistrarPruebaOrdenMutation()
  const anularPagoMut = useAnularPagoLaboratorioMutation()
  const rol = obtenerSesion()?.usuario.rol
  const esOdontologo = rol === 'ODONTOLOGO'
  const puedeAnular = rol === 'ADMIN_GENERAL' || rol === 'ADMIN_SEDE'
  const { data: pagos = [], isLoading: cargandoPagos, isError: errorPagos } =
    usePagosLaboratorio(ordenId || undefined, !esOdontologo)

  const [resultadoPrueba, setResultadoPrueba] = useState<ResultadoPrueba>('CORRECTO')
  const [notasPrueba, setNotasPrueba] = useState('')
  const [nuevaFechaEntrega, setNuevaFechaEntrega] = useState('')
  const [errorTransicion, setErrorTransicion] = useState('')
  const [pagoAbierto, setPagoAbierto] = useState(false)
  const [pagoAAnular, setPagoAAnular] = useState<PagoLaboratorio | null>(null)
  const [motivoAnulacion, setMotivoAnulacion] = useState('')
  const [errorAnulacion, setErrorAnulacion] = useState('')

  if (!abierto || !ordenId) return null

  const handleRegistrarPrueba = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorTransicion('')
    try {
      await registrarPruebaMut.mutateAsync({
        id: ordenId,
        payload: {
          resultado: resultadoPrueba,
          notas: notasPrueba.trim() || undefined,
          nuevaFechaEntrega: resultadoPrueba === 'NECESITA_AJUSTE' && nuevaFechaEntrega ? nuevaFechaEntrega : undefined,
        },
      })
      setResultadoPrueba('CORRECTO')
      setNotasPrueba('')
      setNuevaFechaEntrega('')
    } catch (err: unknown) {
      setErrorTransicion(err instanceof Error ? err.message : 'No se pudo registrar la prueba')
    }
  }

  const handleRecibidoDelLaboratorio = async () => {
    setErrorTransicion('')
    try {
      await cambiarEstadoMut.mutateAsync({
        id: ordenId,
        payload: { nuevoEstado: 'EN_PRUEBA', motivo: `Llegó del laboratorio: prueba ${(orden?.pruebas?.length ?? 0) + 1} con el paciente` },
      })
    } catch (err: unknown) {
      setErrorTransicion(err instanceof Error ? err.message : 'No se pudo registrar la recepción')
    }
  }

  const handleCancelarOrden = async () => {
    const motivoCancelacion = window.prompt('Motivo de la cancelación de la orden:')
    if (!motivoCancelacion?.trim()) return
    setErrorTransicion('')
    try {
      await cambiarEstadoMut.mutateAsync({ id: ordenId, payload: { nuevoEstado: 'CANCELADO', motivo: motivoCancelacion.trim() } })
    } catch (err: unknown) {
      setErrorTransicion(err instanceof Error ? err.message : 'No se pudo cancelar la orden')
    }
  }

  const handleConfirmarAnulacion = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!pagoAAnular) return
    const motivoTrim = motivoAnulacion.trim()
    if (!motivoTrim) {
      setErrorAnulacion('El motivo de anulación es obligatorio.')
      return
    }
    setErrorAnulacion('')
    try {
      await anularPagoMut.mutateAsync({
        pagoId: pagoAAnular.id,
        payload: { motivoAnulacion: motivoTrim },
        ordenId: ordenId || undefined,
      })
      setPagoAAnular(null)
      setMotivoAnulacion('')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al anular el pago'
      setErrorAnulacion(msg)
    }
  }

  return (
    <>
      <Dialog open={abierto} onOpenChange={(v) => !v && onCerrar()}>
        <DialogContent className="max-w-3xl max-h-[92vh] overflow-y-auto">
          <DialogHeader>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200/80 pb-3">
              <div className="flex items-center gap-2.5">
                <div className="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-200 flex items-center justify-center text-indigo-700 font-bold">
                  <FlaskConical className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <DialogTitle className="text-lg font-bold text-slate-900">
                      {orden?.codigo ?? 'Orden de Laboratorio'}
                    </DialogTitle>
                    {orden && <EstadoOrdenBadge estado={orden.estado} tamano="sm" />}
                  </div>
                  <DialogDescription className="text-xs text-slate-500">
                    {orden?.tipoTrabajo} · Sede {orden?.sedeId}
                  </DialogDescription>
                </div>
              </div>

              {orden && onEditar && orden.estado !== 'CANCELADO' && orden.estado !== 'FINALIZADO' && (
                <Button
                  size="sm"
                  variant="secundario"
                  onClick={() => {
                    onEditar(orden)
                  }}
                >
                  Editar Orden
                </Button>
              )}
            </div>
          </DialogHeader>

          {isLoading ? (
            <div className="py-12 text-center text-sm text-slate-500">
              Cargando expediente técnico de laboratorio...
            </div>
          ) : error || !orden ? (
            <div className="py-8 text-center text-rose-600 text-sm">
              No se pudo cargar la información de la orden.
            </div>
          ) : (
            <div className="space-y-5 pt-2">
              {/* Visual Stepper */}
              <div className="bg-slate-50/80 p-3.5 rounded-2xl border border-slate-200/80">
                <TimelineStepper estadoActual={orden.estado} pruebasHechas={orden.pruebas?.length ?? 0} pruebas={orden.pruebas ?? []} />
              </div>

              {/* Grid de Información Principal */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {/* Paciente */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 shadow-sm space-y-1">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                    <User className="w-3.5 h-3.5 text-indigo-500" />
                    Paciente
                  </span>
                  <p className="text-sm font-bold text-slate-900">{orden.pacienteNombre}</p>
                  <p className="text-xs text-slate-500">DNI: {orden.pacienteDni || 'No registrado'}</p>
                </div>

                {/* Odontólogo */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 shadow-sm space-y-1">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                    <Stethoscope className="w-3.5 h-3.5 text-indigo-500" />
                    Odontólogo
                  </span>
                  <p className="text-sm font-bold text-slate-900">{orden.especialistaNombre}</p>
                  <p className="text-xs text-slate-500">Sede: {orden.sedeId}</p>
                </div>

                {/* Laboratorio Externo */}
                <div className="p-3 bg-white rounded-xl border border-slate-200 shadow-sm space-y-1">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                    <FlaskConical className="w-3.5 h-3.5 text-amber-500" />
                    Laboratorio
                  </span>
                  <p className="text-sm font-bold text-slate-900">{orden.laboratorioNombre}</p>
                  <p className="text-xs text-slate-500">
                    Guía: <span className="font-mono">{orden.guiaRemision || 'Sin guía'}</span>
                  </p>
                </div>
              </div>

              {/* Especificaciones Protésicas Clínicas */}
              <div className="p-4 bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                  <FileText className="w-4 h-4 text-indigo-600" />
                  Especificaciones Técnicas Protésicas
                </h4>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                  <div>
                    <span className="text-slate-400 block font-medium">Piezas Dentales:</span>
                    <span className="font-bold text-slate-800 font-mono text-sm">
                      {orden.piezasDentales || 'No especificado'}
                    </span>
                  </div>

                  <div>
                    <span className="text-slate-400 block font-medium">Color / Tono VITA:</span>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      <span className="w-3.5 h-4 rounded-sm border border-slate-300 bg-[#F0EBD7] shadow-sm" />
                      <span className="font-bold text-slate-800 text-sm">
                        {orden.color || orden.colorTono || 'A2 Estándar'}
                      </span>
                    </div>
                  </div>

                  <div>
                    <span className="text-slate-400 block font-medium">Material:</span>
                    <span className="font-bold text-slate-800 text-sm">
                      {orden.material || 'No especificado'}
                    </span>
                  </div>

                  <div>
                    <span className="text-slate-400 block font-medium">Condición:</span>
                    {orden.provisional ? (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-300">
                        Provisional
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-300">
                        Definitiva
                      </span>
                    )}
                  </div>
                </div>

                {orden.indicacionesTecnicas && (
                  <div className="mt-2 pt-2.5 border-t border-slate-100">
                    <span className="text-slate-400 text-xs block font-medium mb-1">
                      Instrucciones Técnicas:
                    </span>
                    <p className="text-xs text-slate-800 bg-slate-50 p-2.5 rounded-lg border border-slate-200/80 leading-relaxed font-mono whitespace-pre-wrap">
                      {orden.indicacionesTecnicas}
                    </p>
                  </div>
                )}

                {orden.observaciones && (
                  <div className="mt-1">
                    <span className="text-slate-400 text-xs block font-medium mb-1">
                      Observaciones Clínicas:
                    </span>
                    <p className="text-xs text-slate-600 bg-slate-50/50 p-2 rounded-lg border border-slate-100">
                      {orden.observaciones}
                    </p>
                  </div>
                )}
              </div>

              {!esOdontologo && (
                <section aria-label="Finanzas del laboratorio" className="space-y-3 rounded-xl border border-emerald-200 bg-emerald-50/50 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <h4 className="text-sm font-bold text-slate-900">Finanzas del laboratorio</h4>
                    {orden.costoLaboratorio != null && orden.estado !== 'CANCELADO' && orden.estadoPago !== 'PAGADO' && (
                      <Button size="sm" variant="primario" onClick={() => setPagoAbierto(true)}>
                        Registrar pago al laboratorio
                      </Button>
                    )}
                  </div>

                  {orden.costoLaboratorio == null ? (
                    <p className="text-sm text-slate-600">No se registró el costo del laboratorio para esta orden.</p>
                  ) : (
                    <>
                      <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                        <Finanza etiqueta="Costo del laboratorio" valor={orden.costoLaboratorio} />
                        <Finanza etiqueta="Precio al paciente" valor={orden.precioPaciente} />
                        <Finanza etiqueta="Ganancia" valor={orden.ganancia} />
                        <div>
                          <span className="block text-xs text-slate-500">Estado de pago</span>
                          <strong>{etiquetaEstadoPago(orden.estadoPago)}</strong>
                        </div>
                        <Finanza etiqueta="Pagado" valor={orden.montoPagado} />
                        <Finanza etiqueta="Falta" valor={orden.saldoLaboratorio} />
                      </div>
                      <div className="border-t border-emerald-200 pt-3">
                        <h5 className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-600">Historial de pagos</h5>
                        {cargandoPagos ? (
                          <p className="text-sm text-slate-500">Cargando pagos…</p>
                        ) : errorPagos ? (
                          <p role="alert" className="text-sm text-rose-700">No se pudo cargar el historial de pagos.</p>
                        ) : pagos.length === 0 ? (
                          <p className="text-sm text-slate-500">Todavía no hay pagos registrados.</p>
                        ) : (
                          <ul className="space-y-2">
                            {pagos.map((pago) => {
                              const esAnulado = Boolean(pago.anulado)
                              return (
                                <li
                                  key={pago.id}
                                  className={`flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-sm rounded-lg p-2 ${
                                    esAnulado ? 'bg-slate-100/60 text-slate-400' : ''
                                  }`}
                                >
                                  <div className="flex flex-col gap-0.5">
                                    <div className="flex items-center gap-2">
                                      <span className={esAnulado ? 'line-through text-slate-400' : ''}>
                                        {new Date(pago.pagadoEn).toLocaleString()} · {pago.metodoPago}
                                      </span>
                                      {esAnulado && (
                                        <Badge tono="peligro" className="no-underline">
                                          Anulado
                                        </Badge>
                                      )}
                                    </div>
                                    {esAnulado && pago.motivoAnulacion && (
                                      <span className="text-xs text-rose-600 italic">
                                        Motivo: {pago.motivoAnulacion}
                                      </span>
                                    )}
                                    {pago.notas && <span className="text-xs text-slate-500">{pago.notas}</span>}
                                  </div>
                                  <div className="flex items-center gap-3">
                                    <strong className={esAnulado ? 'line-through text-slate-400' : 'text-slate-900'}>
                                      S/ {pago.monto.toFixed(2)}
                                    </strong>
                                    {puedeAnular && !esAnulado && (
                                      <Button
                                        size="sm"
                                        variant="secundario"
                                        className="text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                                        onClick={() => {
                                          setPagoAAnular(pago)
                                          setMotivoAnulacion('')
                                          setErrorAnulacion('')
                                        }}
                                      >
                                        Anular
                                      </Button>
                                    )}
                                  </div>
                                </li>
                              )
                            })}
                          </ul>
                        )}
                      </div>
                    </>
                  )}
                </section>
              )}

              {/* Fechas de Seguimiento */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 p-3 bg-slate-50 rounded-xl border border-slate-200/80 text-xs">
                <div>
                  <span className="text-slate-400 block">Solicitud:</span>
                  <span className="font-medium text-slate-800">{orden.fechaSolicitud}</span>
                </div>
                <div>
                  <span className="text-slate-400 block">Estimada Entrega:</span>
                  <span className="font-semibold text-indigo-700">
                    {orden.fechaEstimadaEntrega || 'Por definir'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block">Recepción Clínica:</span>
                  <span className="font-medium text-slate-800">
                    {orden.fechaRecepcionClinica
                      ? new Date(orden.fechaRecepcionClinica).toLocaleDateString()
                      : 'Pendiente'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-400 block">Instalado / Cementado:</span>
                  <span className="font-medium text-slate-800">
                    {orden.fechaInstalacion
                      ? new Date(orden.fechaInstalacion).toLocaleDateString()
                      : 'Pendiente'}
                  </span>
                </div>
              </div>

              {/* Pruebas del trabajo con el paciente */}
              <div className="p-4 bg-indigo-50/50 rounded-xl border border-indigo-100 space-y-3">
                <h4 className="text-xs font-bold text-indigo-900 uppercase tracking-wider flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                  Pruebas con el paciente
                </h4>

                {(orden.pruebas ?? []).length > 0 ? (
                  <ul className="space-y-1.5">
                    {(orden.pruebas ?? []).map((p) => (
                      <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-white px-3 py-2 text-xs border border-slate-200">
                        <span className="font-semibold text-slate-800">Prueba {p.numero}</span>
                        <span className={p.resultado === 'CORRECTO' ? 'font-semibold text-emerald-700' : 'font-semibold text-amber-700'}>
                          {p.resultado === 'CORRECTO' ? 'Correcta' : 'Necesitó ajuste'}
                        </span>
                        <span className="text-slate-500">{new Date(p.fecha + 'T00:00:00').toLocaleDateString()}</span>
                        {p.notas && <span className="basis-full text-slate-600 italic">{p.notas}</span>}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-xs text-slate-500">Todavía no hay pruebas. Cuando el paciente pruebe el trabajo, regístralo aquí.</p>
                )}

                {errorTransicion && (
                  <div className="p-2.5 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-xs flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{errorTransicion}</span>
                  </div>
                )}

                {orden.estado === 'ENVIADO' && (
                  <div className="flex flex-col gap-2 border-t border-indigo-100 pt-3">
                    <p className="text-xs text-slate-600">
                      El trabajo está en el laboratorio. Cuando llegue a la clínica, márcalo para empezar la prueba {(orden.pruebas?.length ?? 0) + 1} con el paciente.
                    </p>
                    <div className="flex items-center justify-between gap-2">
                      <Button size="sm" variant="secundario" type="button" onClick={() => void handleCancelarOrden()} disabled={cambiarEstadoMut.isPending}>
                        Cancelar orden
                      </Button>
                      <Button size="sm" variant="primario" type="button" onClick={() => void handleRecibidoDelLaboratorio()} disabled={cambiarEstadoMut.isPending}>
                        {cambiarEstadoMut.isPending ? 'Guardando...' : `Llegó del laboratorio: empezar prueba ${(orden.pruebas?.length ?? 0) + 1}`}
                      </Button>
                    </div>
                  </div>
                )}

                {orden.estado === 'EN_PRUEBA' && (
                  <form onSubmit={handleRegistrarPrueba} className="space-y-3 border-t border-indigo-100 pt-3">
                    <fieldset className="space-y-1.5">
                      <legend className="text-xs font-semibold text-slate-700">
                        Resultado de la prueba {(orden.pruebas?.length ?? 0) + 1}
                      </legend>
                      <label className="flex items-center gap-2 text-xs text-slate-800">
                        <input type="radio" name={`${uid}-resultado`} checked={resultadoPrueba === 'CORRECTO'} onChange={() => setResultadoPrueba('CORRECTO')} />
                        Correcta: le queda bien, se finaliza el trabajo
                      </label>
                      <label className="flex items-center gap-2 text-xs text-slate-800">
                        <input type="radio" name={`${uid}-resultado`} checked={resultadoPrueba === 'NECESITA_AJUSTE'} onChange={() => setResultadoPrueba('NECESITA_AJUSTE')} />
                        Necesita ajuste: vuelve al laboratorio
                      </label>
                    </fieldset>

                    {resultadoPrueba === 'NECESITA_AJUSTE' && (
                      <div className="flex flex-col gap-1">
                        <label htmlFor={`${uid}-nueva-entrega`} className="text-xs font-semibold text-slate-700">
                          Nueva fecha de entrega del laboratorio (opcional)
                        </label>
                        <input
                          id={`${uid}-nueva-entrega`}
                          type="date"
                          value={nuevaFechaEntrega}
                          onChange={(e) => setNuevaFechaEntrega(e.target.value)}
                          className="h-9 px-3 rounded-lg border border-slate-300 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                        />
                      </div>
                    )}

                    <div className="flex flex-col gap-1">
                      <label htmlFor={`${uid}-notas-prueba`} className="text-xs font-semibold text-slate-700">
                        Qué se observó (opcional)
                      </label>
                      <input
                        id={`${uid}-notas-prueba`}
                        type="text"
                        placeholder="Ej. Alto en la cúspide, falta ajustar el color..."
                        value={notasPrueba}
                        onChange={(e) => setNotasPrueba(e.target.value)}
                        className="h-9 px-3 rounded-lg border border-slate-300 bg-white text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      />
                    </div>

                    <div className="flex items-center justify-between gap-2 pt-1">
                      <Button size="sm" variant="secundario" type="button" onClick={() => void handleCancelarOrden()} disabled={cambiarEstadoMut.isPending}>
                        Cancelar orden
                      </Button>
                      <Button size="sm" variant="primario" type="submit" disabled={registrarPruebaMut.isPending}>
                        {registrarPruebaMut.isPending ? 'Guardando...' : `Registrar prueba ${(orden.pruebas?.length ?? 0) + 1}`}
                      </Button>
                    </div>
                  </form>
                )}
              </div>

              {/* Historial de Transiciones / Trazabilidad Auditada */}
              <div className="space-y-2 pt-2">
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-slate-500" />
                  Historial de Trazabilidad & Auditoría
                </h4>

                <div className="space-y-2 max-h-52 overflow-y-auto pr-1">
                  {orden.historial && orden.historial.length > 0 ? (
                    orden.historial.map((item) => (
                      <div
                        key={item.id}
                        className="p-2.5 bg-slate-50 rounded-xl border border-slate-200/80 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                      >
                        <div className="space-y-0.5">
                          <div className="flex items-center gap-1.5">
                            {item.estadoAnterior && (
                              <>
                                <span className="font-semibold text-slate-600">{item.estadoAnterior}</span>
                                <span className="text-slate-400">→</span>
                              </>
                            )}
                            <strong className="text-indigo-900">{item.estadoNuevo}</strong>
                          </div>
                          <p className="text-slate-600 italic">
                            {item.motivo || 'Transición registrada'}
                          </p>
                        </div>

                        <div className="text-right text-xs text-slate-400 shrink-0">
                          <span className="font-semibold text-slate-700 block">
                            {item.usuarioNombre} ({item.usuarioRol})
                          </span>
                          <span>{new Date(item.creadoEn).toLocaleString()}</span>
                        </div>
                      </div>
                    ))
                  ) : (
                    <p className="text-xs text-slate-400 italic py-2">
                      No se registran eventos adicionales de trazabilidad.
                    </p>
                  )}
                </div>
              </div>
            </div>
          )}
          {orden && !esOdontologo && (
            <RegistrarPagoLaboratorioModal
              abierto={pagoAbierto}
              onCerrar={() => setPagoAbierto(false)}
              ordenId={orden.id}
              saldoPendiente={orden.saldoLaboratorio ?? 0}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* Diálogo de Anulación de Pago */}
      <Dialog open={Boolean(pagoAAnular)} onOpenChange={(v) => !v && setPagoAAnular(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Anular pago de laboratorio</DialogTitle>
            <DialogDescription>
              Esta acción revertirá el egreso en caja registrando un ingreso compensatorio. Ingrese el motivo obligatorio.
            </DialogDescription>
          </DialogHeader>

          {errorAnulacion && (
            <div role="alert" className="p-2.5 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorAnulacion}</span>
            </div>
          )}

          <form onSubmit={handleConfirmarAnulacion} className="space-y-4">
            {pagoAAnular && (
              <div className="p-3 bg-slate-50 rounded-lg text-xs space-y-1">
                <p><strong>Monto a anular:</strong> S/ {pagoAAnular.monto.toFixed(2)}</p>
                <p><strong>Método de pago:</strong> {pagoAAnular.metodoPago}</p>
                <p><strong>Fecha de pago:</strong> {new Date(pagoAAnular.pagadoEn).toLocaleString()}</p>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-motivo-anulacion`} className="text-xs font-semibold text-slate-700">
                Motivo de anulación <span className="text-rose-500">*</span>
              </label>
              <textarea
                id={`${uid}-motivo-anulacion`}
                value={motivoAnulacion}
                onChange={(e) => setMotivoAnulacion(e.target.value)}
                required
                rows={3}
                placeholder="Indique la razón de la anulación (ej. error de monto, pago duplicado...)"
                className="w-full p-2.5 rounded-lg border border-slate-300 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="secundario"
                size="sm"
                onClick={() => setPagoAAnular(null)}
                disabled={anularPagoMut.isPending}
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                variant="peligro"
                size="sm"
                disabled={!motivoAnulacion.trim() || anularPagoMut.isPending}
              >
                {anularPagoMut.isPending ? 'Anulando...' : 'Confirmar Anulación'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}

function Finanza({ etiqueta, valor }: { etiqueta: string; valor?: number | null }) {
  return (
    <div>
      <span className="block text-xs text-slate-500">{etiqueta}</span>
      <strong className="text-slate-900">{valor == null ? '—' : `S/ ${valor.toFixed(2)}`}</strong>
    </div>
  )
}

function etiquetaEstadoPago(estado?: string | null) {
  if (estado === 'PAGADO') return 'Pagado'
  if (estado === 'PARCIAL') return 'Parcial'
  return 'Pendiente'
}
