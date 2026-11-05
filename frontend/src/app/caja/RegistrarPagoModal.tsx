import { METODOS_PAGO, nombreMetodoPago, type MetodoPago } from '@/shared/metodosPago'
import { useEffect, useState } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { Alert } from '@/design-system/components/Alert'
import { formatSoles } from '@/shared/format'
import { textoDeError } from '@/shared/http'
import { useRegistrarPagoMutation, type PagoDTO } from './api-caja'
import { listarPacientes, type PacienteDTO } from '../pacientes/api-pacientes'
import {
  useCrearTratamientoMutation,
  useServiciosCatalogo,
  useTratamientosPaciente,
  type TratamientoDTO,
} from '../tratamientos/api-tratamientos'
import { crearSolicitudPlanSimple } from '../tratamientos/planSimple'
import { useSesion } from '@/shared/auth'
import { useEspecialistas } from '../agenda/api-agenda'

const COMPROBANTE_INTERNO = 'RECIBO'

interface Props {
  abierto: boolean
  alCerrar: () => void
  sedeId: string
  pacienteInicialId?: string
  pacienteInicialNombre?: string
  tratamientoInicialId?: string
  saldoPendienteSugerido?: number
  alGuardar?: (pago: PagoDTO) => void
}

export function RegistrarPagoModal({
  abierto,
  alCerrar,
  sedeId,
  pacienteInicialId,
  pacienteInicialNombre,
  tratamientoInicialId,
  saldoPendienteSugerido,
  alGuardar,
}: Props) {
  const [pacienteId, setPacienteId] = useState(pacienteInicialId ?? '')
  const [listaPacientes, setListaPacientes] = useState<{ sedeId: string; pacientes: PacienteDTO[] } | null>(null)
  const [errorPacientes, setErrorPacientes] = useState<string | null>(null)
  const [tratamientoId, setTratamientoId] = useState(tratamientoInicialId ?? '')
  const [monto, setMonto] = useState(
    saldoPendienteSugerido && saldoPendienteSugerido > 0 ? String(saldoPendienteSugerido) : ''
  )
  const [metodoPago, setMetodoPago] = useState<MetodoPago>('EFECTIVO')
  const [servicioId, setServicioId] = useState('')
  const [nombreServicio, setNombreServicio] = useState('')
  const [costoServicio, setCostoServicio] = useState('')
  const [especialistaElegido, setEspecialistaId] = useState('')
  const [planCreado, setPlanCreado] = useState<TratamientoDTO | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)
  const [pagoExitoso, setPagoExitoso] = useState<PagoDTO | null>(null)

  const registrarMutation = useRegistrarPagoMutation()
  const crearTratamiento = useCrearTratamientoMutation()
  const tratamientosQuery = useTratamientosPaciente(pacienteId || undefined)
  const tratamientos = tratamientosQuery.data ?? []
  const planesConSaldo = tratamientos.filter((tratamiento) =>
    tratamiento.estado !== 'CANCELADO' && (tratamiento.saldoPendiente ?? 0) > 0
  )
  const { data: catalogo = [] } = useServiciosCatalogo(sedeId, abierto && Boolean(pacienteId))
  const { data: especialistas = [] } = useEspecialistas(sedeId, abierto && Boolean(pacienteId))
  // Un odontólogo solo cobra procedimientos a su nombre (las metas se miden por doctor).
  const sesion = useSesion()
  const esDoctor = sesion?.usuario.rol === 'ODONTOLOGO'
  const miEspecialista = esDoctor ? especialistas.find((e) => e.usuarioId === sesion?.usuario.id) : undefined
  const opcionesDoctor = esDoctor ? (miEspecialista ? [miEspecialista] : []) : especialistas
  const especialistaId = esDoctor ? (miEspecialista?.id ?? '') : especialistaElegido
  const pacientes = listaPacientes?.pacientes ?? []
  const necesitaLista = abierto && !pacienteInicialId
  const cargandoPacientes = necesitaLista && listaPacientes?.sedeId !== sedeId
  const tratamientoSeleccionado = planesConSaldo.find((tratamiento) => tratamiento.id === tratamientoId)
  const importe = Number(monto)
  const saldoPlan = tratamientoSeleccionado?.saldoPendiente ?? 0
  const importeValido = Number.isFinite(importe) && importe > 0
  const pendiente = registrarMutation.isPending || crearTratamiento.isPending

  useEffect(() => {
    if (!necesitaLista) return
    let vigente = true
    listarPacientes({ size: 50, sedeId })
      .then((res) => {
        if (vigente) {
          setListaPacientes({ sedeId, pacientes: res })
          setErrorPacientes(null)
        }
      })
      .catch((error: unknown) => {
        if (vigente) setErrorPacientes(textoDeError(error, 'No se pudo cargar la lista de pacientes.'))
      })
    return () => {
      vigente = false
    }
  }, [necesitaLista, sedeId])

  useAlCambiar([abierto, pacienteInicialId, tratamientoInicialId, saldoPendienteSugerido], () => {
    if (!abierto) {
      setPagoExitoso(null)
      setErrorLocal(null)
      setPlanCreado(null)
      return
    }
    if (pacienteInicialId) setPacienteId(pacienteInicialId)
    if (tratamientoInicialId) setTratamientoId(tratamientoInicialId)
    if (saldoPendienteSugerido && saldoPendienteSugerido > 0) setMonto(String(saldoPendienteSugerido))
    setPagoExitoso(null)
    setErrorLocal(null)
  })

  useAlCambiar([pacienteId, tratamientosQuery.data, tratamientoInicialId], () => {
    if (!tratamientosQuery.isSuccess) return
    const elegibles = (tratamientosQuery.data ?? []).filter((tratamiento) =>
      tratamiento.estado !== 'CANCELADO' && (tratamiento.saldoPendiente ?? 0) > 0
    )
    const seleccionado = elegibles.find((tratamiento) => tratamiento.id === tratamientoInicialId)
      ?? elegibles.find((tratamiento) => tratamiento.id === tratamientoId)
      ?? elegibles[0]
    setTratamientoId(seleccionado?.id ?? '')
    if (seleccionado?.saldoPendiente != null) setMonto(String(seleccionado.saldoPendiente))
  })

  function elegirPaciente(id: string) {
    setPacienteId(id)
    setTratamientoId('')
    setMonto('')
    setPlanCreado(null)
    setServicioId('')
    setNombreServicio('')
    setCostoServicio('')
    setEspecialistaId('')
    setErrorLocal(null)
  }

  function elegirServicio(id: string) {
    const servicio = catalogo.find((elemento) => elemento.id === id)
    setServicioId(id)
    setNombreServicio(servicio?.nombre ?? '')
    if (servicio?.precio != null && servicio.precio > 0) {
      setCostoServicio(servicio.precio.toFixed(2))
      setMonto(servicio.precio.toFixed(2))
    }
  }

  async function manejarEnvio(evento: React.FormEvent) {
    evento.preventDefault()
    setErrorLocal(null)
    if (!pacienteId) {
      setErrorLocal('Selecciona un paciente.')
      return
    }
    if (tratamientosQuery.isPending || tratamientosQuery.isError) {
      setErrorLocal(tratamientosQuery.isError
        ? textoDeError(tratamientosQuery.error, 'No se pudieron cargar los planes del paciente.')
        : 'Espera a que se carguen los planes del paciente.')
      return
    }
    if (!importeValido) {
      setErrorLocal('El monto a cobrar debe ser mayor a cero.')
      return
    }

    let planPreparado = planCreado
    try {
      let idTratamiento = tratamientoSeleccionado?.id
      if (tratamientoSeleccionado && importe > saldoPlan) {
        setErrorLocal(`El monto no puede superar el saldo disponible de ${formatSoles(saldoPlan)}.`)
        return
      }

      if (planesConSaldo.length === 0) {
        if (!planCreado && (!nombreServicio.trim() || !Number.isFinite(Number(costoServicio)) ||
            Number(costoServicio) <= 0 || !especialistaId)) {
          setErrorLocal('Elige el procedimiento, su precio y el doctor responsable.')
          return
        }
        if (!planCreado && importe > Number(costoServicio)) {
          setErrorLocal(`El monto no puede superar el saldo disponible de ${formatSoles(Number(costoServicio))}.`)
          return
        }
        let plan = planCreado
        if (!plan) {
          plan = await crearTratamiento.mutateAsync(crearSolicitudPlanSimple({
            pacienteId,
            sedeId,
            especialistaId,
            nombre: nombreServicio,
            costoTotal: Number(costoServicio),
            totalSesiones: 1,
          }))
          if (!plan.id) {
            setErrorLocal('El servidor no devolvió el identificador del plan.')
            return
          }
          setPlanCreado(plan)
          planPreparado = plan
        }
        if (importe > Number(plan.saldoPendiente ?? plan.costoTotal ?? 0)) {
          setErrorLocal(`El monto no puede superar el saldo disponible de ${formatSoles(plan.saldoPendiente ?? plan.costoTotal ?? 0)}.`)
          return
        }
        idTratamiento = plan.id
      }

      if (!idTratamiento) {
        setErrorLocal('Selecciona un plan con saldo disponible.')
        return
      }
      const doctorDelPlan = tratamientoSeleccionado?.especialistaId ?? planCreado?.especialistaId
      const pago = await registrarMutation.mutateAsync({
        pacienteId,
        tratamientoId: idTratamiento,
        especialistaId: doctorDelPlan ?? undefined,
        sedeId,
        monto: importe,
        metodoPago,
        numeroOperacion: undefined,
        comprobanteTipo: COMPROBANTE_INTERNO,
      })
      setPagoExitoso(pago)
      alGuardar?.(pago)
    } catch (error) {
      const detalle = textoDeError(error, 'No se pudo registrar el cobro.')
      setErrorLocal(planCreado || planPreparado
        ? `El plan ya está creado; al reintentar se usará el mismo plan. ${detalle}`
        : detalle)
    }
  }

  const servicioElegido = catalogo.find((servicio) => servicio.id === servicioId)
  const doctorElegido = especialistas.find((especialista) => especialista.id === especialistaId)
  const sillon = doctorElegido?.sillonesPorSede?.[sedeId]
  const sesionPendiente = tratamientoSeleccionado
    ? (tratamientoSeleccionado.sesionesRealizadas ?? 0) + 1
    : 1

  return (
    <Dialog open={abierto} onOpenChange={(valor) => !valor && alCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registrar cobro</DialogTitle>
          <DialogDescription>
            Elige un plan con saldo o registra el cobro de una consulta o procedimiento.
          </DialogDescription>
        </DialogHeader>

        {(errorLocal || errorPacientes) && (
          <Alert tono="error" titulo={errorLocal ?? errorPacientes ?? 'No se pudo cargar la información.'} />
        )}

        {pagoExitoso ? (
          <div className="space-y-4 py-2">
            <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 p-5 text-center space-y-3">
              <div className="inline-flex size-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 font-bold text-xl">✓</div>
              <h3 className="font-display text-lg font-bold text-emerald-950">¡Cobro registrado!</h3>
              <p className="text-xs text-emerald-800">
                Comprobante emitido: <strong className="font-mono text-sm text-emerald-950">{pagoExitoso.comprobante}</strong>
              </p>
              <div className="rounded-lg bg-surface-container-lowest border border-outline p-3 text-xs space-y-1.5 text-left">
                <div className="flex justify-between"><span>Monto cobrado</span><strong>{formatSoles(pagoExitoso.monto)}</strong></div>
                <div className="flex justify-between"><span>Método</span><strong>{nombreMetodoPago(pagoExitoso.metodoPago)}</strong></div>
              </div>
            </div>
            <DialogFooter>
              {pacienteInicialNombre && (
                <a
                  href={`https://wa.me/?text=${encodeURIComponent(
                    `*Comprobante de Pago - Odontomax*\nPaciente: ${pacienteInicialNombre}\nComprobante: ${pagoExitoso.comprobante}\nMonto: S/ ${pagoExitoso.monto.toFixed(2)}`
                  )}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center rounded-md border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-800"
                >
                  Enviar recibo por WhatsApp
                </a>
              )}
              <Button type="button" variant="accion" onClick={alCerrar}>Cerrar y actualizar</Button>
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={manejarEnvio} className="space-y-4">
            {pacienteInicialNombre ? (
              <Field label="Paciente">
                <Input value={pacienteInicialNombre} disabled />
              </Field>
            ) : (
              <Field label="Seleccionar paciente *">
                <select
                  id="selectPacienteCobro"
                  value={pacienteId}
                  onChange={(evento) => elegirPaciente(evento.target.value)}
                  required
                  className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                >
                  <option value="">{cargandoPacientes ? 'Cargando pacientes…' : 'Selecciona un paciente'}</option>
                  {pacientes.map((paciente) => (
                    <option key={paciente.id} value={paciente.id}>
                      {paciente.nombreCompleto} (DNI: {paciente.dni})
                    </option>
                  ))}
                </select>
              </Field>
            )}

            {pacienteId && tratamientosQuery.isPending && (
              <p role="status" className="text-sm text-on-surface-variant">Cargando planes del paciente…</p>
            )}
            {pacienteId && tratamientosQuery.isError && (
              <Alert tono="error" titulo={textoDeError(tratamientosQuery.error, 'No se pudieron cargar los planes del paciente.')} />
            )}

            {planesConSaldo.length > 0 && tratamientoSeleccionado && (
              <>
                {planesConSaldo.length > 1 && (
                  <Field label="Plan de tratamiento *">
                    <select
                      value={tratamientoId}
                      onChange={(evento) => {
                        const plan = planesConSaldo.find((elemento) => elemento.id === evento.target.value)
                        setTratamientoId(evento.target.value)
                        setMonto(plan?.saldoPendiente != null ? String(plan.saldoPendiente) : '')
                      }}
                      className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                    >
                      {planesConSaldo.map((plan) => <option key={plan.id} value={plan.id}>{plan.nombre}</option>)}
                    </select>
                  </Field>
                )}
                <div className="rounded-lg border border-outline-variant bg-surface-container-low p-3 text-sm space-y-1">
                  <p className="font-semibold">{tratamientoSeleccionado.nombre}</p>
                  <p className="text-xs text-on-surface-variant">
                    Doctor: {tratamientoSeleccionado.especialistaNombre ?? 'No indicado'}
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    Sesión {sesionPendiente} de {tratamientoSeleccionado.totalSesiones ?? '—'}
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    Presupuesto {formatSoles(tratamientoSeleccionado.costoTotal ?? 0)} · Pagado{' '}
                    {formatSoles((tratamientoSeleccionado.montoPagado ?? 0) + (tratamientoSeleccionado.pagadoAntesSistema ?? 0))} · Falta{' '}
                    {formatSoles(saldoPlan)}
                  </p>
                </div>
              </>
            )}

            {pacienteId && tratamientosQuery.isSuccess && planesConSaldo.length === 0 && (
              <section aria-label="Cobrar consulta o procedimiento" className="space-y-3 rounded-lg border border-outline-variant p-3">
                <h3 className="text-sm font-semibold">Cobrar consulta o procedimiento</h3>
                {tratamientos.length > 0 && !planCreado && (
                  <div className="space-y-1.5">
                    <p className="text-xs font-semibold text-on-surface-variant">Tratamientos anteriores de este paciente (toca uno para repetirlo)</p>
                    <div className="flex flex-wrap gap-1.5">
                      {[...new Set(tratamientos.map((t) => t.nombre).filter((n): n is string => Boolean(n)))].slice(0, 6).map((nombre) => {
                        const delCatalogo = catalogo.find((c) => c.nombre?.toLowerCase() === nombre.toLowerCase())
                        return (
                          <button
                            key={nombre}
                            type="button"
                            onClick={() => (delCatalogo?.id ? elegirServicio(delCatalogo.id) : setNombreServicio(nombre))}
                            className="rounded-pill border border-outline-variant bg-surface-container-lowest px-3 py-1 text-xs font-semibold text-primary hover:border-acento hover:bg-acento/10"
                          >
                            {nombre}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )}
                <Field label="Tratamiento del catálogo *">
                  <select
                    aria-label="Tratamiento del catálogo"
                    value={servicioId}
                    onChange={(evento) => elegirServicio(evento.target.value)}
                    disabled={Boolean(planCreado)}
                    className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                  >
                    <option value="">Selecciona un tratamiento</option>
                    {catalogo.map((servicio) => (
                      <option key={servicio.id} value={servicio.id}>{servicio.nombre}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Nombre del plan *">
                  <Input
                    value={nombreServicio}
                    onChange={(evento) => setNombreServicio(evento.target.value)}
                    required
                    disabled={Boolean(planCreado)}
                  />
                </Field>
                <Field
                  label="Doctor responsable *"
                  ayuda={doctorElegido && (
                    <span>{sillon ? `Sillón ${sillon}` : 'Sillón no asignado'} · {doctorElegido.nombre}</span>
                  )}
                >
                  <select
                    aria-label="Doctor responsable"
                    value={especialistaId}
                    onChange={(evento) => setEspecialistaId(evento.target.value)}
                    required
                    disabled={Boolean(planCreado) || esDoctor}
                    className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                  >
                    {!esDoctor && <option value="">Selecciona un doctor</option>}
                    {opcionesDoctor.map((especialista) => (
                      <option key={especialista.id} value={especialista.id}>{especialista.nombre}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Precio del procedimiento (S/) *">
                  <Input
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={costoServicio}
                    onChange={(evento) => setCostoServicio(evento.target.value)}
                    required
                    disabled={Boolean(planCreado)}
                  />
                </Field>
                {planCreado && (
                  <p role="status" className="text-xs text-on-surface-variant">
                    El plan ya está creado; el reintento no lo duplicará.
                  </p>
                )}
                {servicioElegido && servicioElegido.precio == null && (
                  <p className="text-xs text-on-surface-variant">El catálogo no tiene precio; ingresa el precio acordado.</p>
                )}
              </section>
            )}

            <Field label="Monto a cobrar (S/) *">
              <Input
                id="montoCobro"
                type="number"
                step="0.01"
                min="0.01"
                max={tratamientoSeleccionado ? saldoPlan : undefined}
                value={monto}
                onChange={(evento) => setMonto(evento.target.value)}
                placeholder="0.00"
                required
                ref={enfocarAlMontar}
              />
            </Field>

            <Field label="Método de pago *">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {METODOS_PAGO.map((m) => (
                  <button
                    key={m.valor}
                    type="button"
                    aria-pressed={metodoPago === m.valor}
                    onClick={() => setMetodoPago(m.valor)}
                    className={`rounded-lg border px-3 py-2.5 text-sm font-semibold ${metodoPago === m.valor ? 'border-primary bg-primary-fixed/60 text-primary' : 'border-outline bg-surface-container-low'}`}
                  >
                    {m.nombre}
                  </button>
                ))}
              </div>
            </Field>


            <DialogFooter>
              <Button type="button" variant="secundario" onClick={alCerrar} disabled={pendiente}>Cancelar</Button>
              <Button
                type="submit"
                variant="accion"
                disabled={pendiente || tratamientosQuery.isPending || tratamientosQuery.isError}
              >
                {pendiente ? 'Procesando…' : 'Confirmar cobro'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
