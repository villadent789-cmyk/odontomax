import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
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
import { toast } from '@/design-system'
import { formatSoles, hoyEnLima } from '@/shared/format'
import { textoDeError } from '@/shared/http'
import { useSesion } from '@/shared/auth'
import { METODOS_PAGO, type MetodoPago } from '@/shared/metodosPago'
import { useEspecialistas } from '../agenda/api-agenda'
import { useRegistrarPagoMutation } from '../caja/api-caja'
import { useAplicarDescuentoRecomendacion, useDescuentosRecomendacion } from '../pacientes/api-descuentos-recomendacion'
import {
  useCrearTratamientoMutation,
  useTratamientosPaciente,
  useServiciosCatalogo,
  type ServicioCatalogo,
  type TratamientoDTO,
} from './api-tratamientos'
import { useCrearPlantillaPlan, usePlantillasPlan, type PlantillaPlan } from './api-plantillas-plan'
import { GuardarComoPlantilla, SelectorPlantilla } from './PlantillasDelPlan'
import { crearSolicitudPlanSimple } from './planSimple'

/** Un caso más del mismo tratamiento (otra gravedad), con sus piezas y su costo por pieza. */
interface CasoExtra {
  gravedad: '' | 'LEVE' | 'MODERADO' | 'SEVERO'
  piezas: string
  costoPorPieza: string
}

/** Costo por pieza del catálogo para una gravedad; si no hay uno específico, el precio general. */
function precioUnitarioDe(servicio: ServicioCatalogo | undefined, gravedad: '' | 'LEVE' | 'MODERADO' | 'SEVERO'): number | null {
  if (!servicio) return null
  const porGravedad =
    gravedad === 'LEVE' ? servicio.precioLeve : gravedad === 'MODERADO' ? servicio.precioModerado : gravedad === 'SEVERO' ? servicio.precioSevero : undefined
  const precio = porGravedad ?? servicio.precio
  return precio != null && precio > 0 ? precio : null
}

interface Props {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre?: string
  sedeId: string
  alGuardar?: (tratamiento: TratamientoDTO) => void
  itemsIniciales?: Array<{
    nombreProcedimiento: string
    piezaFdi?: number
    precioBase?: string
    cantidad?: number
  }>
}

export function NuevoTratamientoModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  sedeId,
  alGuardar,
  itemsIniciales,
}: Props) {
  const sedeIdLimpia = sedeId === 'sede-chilca-2' ? 'sede-chilca-1' : (sedeId || 'sede-chilca-1')
  const { data: especialistas = [] } = useEspecialistas(sedeIdLimpia, abierto)
  const { data: catalogo = [] } = useServiciosCatalogo(sedeIdLimpia, abierto)
  const crearTratamiento = useCrearTratamientoMutation()
  const plantillas = usePlantillasPlan(abierto).data ?? []
  const crearPlantilla = useCrearPlantillaPlan()
  const planesDelPaciente = useTratamientosPaciente(abierto ? pacienteId : undefined).data ?? []
  const planesEnCurso = planesDelPaciente.filter((t) => t.estado === 'EN_PROCESO' || t.estado === 'PLANIFICADO')
  const registrarPago = useRegistrarPagoMutation()
  const descuentosRecomendacion = useDescuentosRecomendacion(pacienteId, abierto).data ?? []
  const descuentoPendiente = descuentosRecomendacion.find((d) => d.estado === 'PENDIENTE')
  const aplicarDescuento = useAplicarDescuentoRecomendacion(pacienteId)
  const [darDescuentoRecomendacion, setDarDescuentoRecomendacion] = useState(false)

  const [servicioId, setServicioId] = useState('')
  const [nombre, setNombre] = useState('')
  const [especialistaElegido, setEspecialistaId] = useState('')
  // Un odontólogo solo crea planes a su nombre: no ve a los demás doctores (las metas se miden por doctor).
  const sesion = useSesion()
  const esDoctor = sesion?.usuario.rol === 'ODONTOLOGO'
  const miEspecialista = esDoctor ? especialistas.find((e) => e.usuarioId === sesion?.usuario.id) : undefined
  const opcionesDoctor = esDoctor ? (miEspecialista ? [miEspecialista] : []) : especialistas
  const especialistaId = esDoctor ? (miEspecialista?.id ?? '') : especialistaElegido
  const [costoTotal, setCostoTotal] = useState('')
  const [totalSesiones, setTotalSesiones] = useState('1')
  const [adelanto, setAdelanto] = useState('')
  const [metodoPago, setMetodoPago] = useState<MetodoPago>('EFECTIVO')
  const [observaciones, setObservaciones] = useState('')
  // Con un nombre escrito, el plan también se guarda como plantilla.
  const [plantillaNueva, setPlantillaNueva] = useState('')
  const [usarCuotas, setUsarCuotas] = useState(false)
  const [cuotaInicial, setCuotaInicial] = useState('')
  const [cuotaMensual, setCuotaMensual] = useState('')
  const [cuotasMensuales, setCuotasMensuales] = useState('12')
  const [primerVencimiento, setPrimerVencimiento] = useState(() => hoyEnLima())
  // Costo por pieza: el total es piezas × costo por pieza, y la gravedad del caso puede cambiar el costo.
  const [servicioElegido, setServicioElegido] = useState<ServicioCatalogo | undefined>()
  const [piezas, setPiezas] = useState('1')
  const [gravedad, setGravedad] = useState<'' | 'LEVE' | 'MODERADO' | 'SEVERO'>('')
  const [costoPorPieza, setCostoPorPieza] = useState('')
  // Casos adicionales del mismo tratamiento, cada uno con su gravedad, piezas y costo por pieza.
  const [otrosCasos, setOtrosCasos] = useState<CasoExtra[]>([])
  const [motivoPrecio, setMotivoPrecio] = useState('')
  const [planCreado, setPlanCreado] = useState<TratamientoDTO | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | null>(null)

  useAlCambiar([abierto, itemsIniciales], () => {
    if (!abierto) {
      setServicioId('')
      setNombre('')
      setEspecialistaId('')
      setCostoTotal('')
      setServicioElegido(undefined)
      setPiezas('1')
      setGravedad('')
      setCostoPorPieza('')
      setOtrosCasos([])
      setTotalSesiones('1')
      setAdelanto('')
      setMetodoPago('EFECTIVO')
      setObservaciones('')
      setPlanCreado(null)
      setPlantillaNueva('')
      setDarDescuentoRecomendacion(false)
      setErrorLocal(null)
      return
    }
    if (itemsIniciales?.length) {
      setNombre(itemsIniciales.map((item) => item.nombreProcedimiento).filter(Boolean).join(' + '))
      const total = itemsIniciales.reduce(
        (suma, item) => suma + (Number(item.precioBase) || 0) * (item.cantidad || 1),
        0,
      )
      if (total > 0) setCostoTotal(total.toFixed(2))
    }
  })

  const especialistaSeleccionado = especialistas.find((especialista) => especialista.id === especialistaId)
  const sillon = especialistaSeleccionado?.sillonesPorSede?.[sedeIdLimpia]
  const nCuotas = Number(cuotasMensuales)
  const importe = usarCuotas ? (Number(cuotaInicial) || 0) + (Number(cuotaMensual) || 0) * (Number.isFinite(nCuotas) ? nCuotas : 0) : Number(costoTotal)
  const nPiezas = Number(piezas)
  const tieneCostoPorGravedad = [servicioElegido?.precioLeve, servicioElegido?.precioModerado, servicioElegido?.precioSevero].some((p) => p != null)
  const casosParaCatalogo = [{ gravedad, piezas: nPiezas }, ...otrosCasos.map((c) => ({ gravedad: c.gravedad, piezas: Number(c.piezas) }))]
  const unitariosCatalogo = casosParaCatalogo.map((c) => precioUnitarioDe(servicioElegido, c.gravedad))
  const precioCatalogo =
    casosParaCatalogo.every((c, i) => unitariosCatalogo[i] != null && Number.isInteger(c.piezas) && c.piezas >= 1)
      ? Math.round(casosParaCatalogo.reduce((suma, c, i) => suma + (unitariosCatalogo[i] ?? 0) * c.piezas, 0) * 100) / 100
      : null
  const precioAjustado = !usarCuotas && precioCatalogo !== null && Number.isFinite(importe) && Math.abs(importe - precioCatalogo) > 0.005
  const importeAdelanto = adelanto.trim() ? Number(adelanto) : 0
  const pendiente = crearTratamiento.isPending || registrarPago.isPending

  /** El total sigue a las piezas y al costo por pieza; después se puede acordar otro total (con su motivo). */
  function recalcularTotal(nuevasPiezas: string, nuevoCosto: string, casos: CasoExtra[] = otrosCasos) {
    const p = Number(nuevasPiezas)
    const c = Number(nuevoCosto)
    if (Number.isInteger(p) && p >= 1 && Number.isFinite(c) && c >= 0 && nuevoCosto.trim() !== '') {
      const extras = casos.reduce((suma, caso) => suma + (Number(caso.piezas) || 0) * (Number(caso.costoPorPieza) || 0), 0)
      setCostoTotal((p * c + extras).toFixed(2))
    }
  }

  function cambiarCaso(indice: number, cambios: Partial<CasoExtra>) {
    const nuevos = otrosCasos.map((caso, i) => (i === indice ? { ...caso, ...cambios } : caso))
    setOtrosCasos(nuevos)
    recalcularTotal(piezas, costoPorPieza, nuevos)
  }

  function elegirGravedadDeCaso(indice: number, nueva: '' | 'LEVE' | 'MODERADO' | 'SEVERO') {
    const unitario = precioUnitarioDe(servicioElegido, nueva)
    cambiarCaso(indice, unitario != null ? { gravedad: nueva, costoPorPieza: unitario.toFixed(2) } : { gravedad: nueva })
  }

  function agregarCaso() {
    const nuevos = [...otrosCasos, { gravedad: '' as const, piezas: '1', costoPorPieza: servicioElegido?.precio != null ? Number(servicioElegido.precio).toFixed(2) : '' }]
    setOtrosCasos(nuevos)
    recalcularTotal(piezas, costoPorPieza, nuevos)
  }

  function quitarCaso(indice: number) {
    const nuevos = otrosCasos.filter((_, i) => i !== indice)
    setOtrosCasos(nuevos)
    recalcularTotal(piezas, costoPorPieza, nuevos)
  }

  function elegirServicio(servicio: ServicioCatalogo | undefined) {
    if (!servicio) return
    setServicioId(servicio.id ?? '')
    setServicioElegido(servicio)
    setOtrosCasos([])
    setNombre(servicio.nombre ?? '')
    // Sin costos por gravedad, la gravedad no se guarda: no aplica a este servicio.
    if (![servicio.precioLeve, servicio.precioModerado, servicio.precioSevero].some((p) => p != null)) setGravedad('')
    const unitario = precioUnitarioDe(servicio, gravedad)
    if (unitario != null) {
      setCostoPorPieza(unitario.toFixed(2))
      recalcularTotal(piezas, unitario.toFixed(2), [])
    }
  }

  /** Precarga el plan con la plantilla: el precio sale siempre del catálogo de hoy. */
  function aplicarPlantilla(p: PlantillaPlan) {
    const servicio = catalogo.find((s) => s.id === p.procedimientoId)
    if (!servicio) {
      toast.error(`El servicio «${p.servicioNombre}» ya no está en el catálogo.`)
      return
    }
    const conGravedad = [servicio.precioLeve, servicio.precioModerado, servicio.precioSevero].some((x) => x != null)
    const gravedadDePlantilla = conGravedad ? (p.gravedad ?? '') : ''
    const unitario = precioUnitarioDe(servicio, gravedadDePlantilla)
    setServicioId(servicio.id ?? '')
    setServicioElegido(servicio)
    setNombre(p.nombre)
    setPiezas(String(p.piezas))
    setGravedad(gravedadDePlantilla)
    setOtrosCasos([])
    setTotalSesiones(String(p.sesiones))
    setObservaciones(p.notas ?? '')
    if (unitario != null) {
      setCostoPorPieza(unitario.toFixed(2))
      recalcularTotal(String(p.piezas), unitario.toFixed(2), [])
    }
  }

  function elegirGravedad(nueva: '' | 'LEVE' | 'MODERADO' | 'SEVERO') {
    setGravedad(nueva)
    const unitario = precioUnitarioDe(servicioElegido, nueva)
    if (unitario != null) {
      setCostoPorPieza(unitario.toFixed(2))
      recalcularTotal(piezas, unitario.toFixed(2))
    }
  }

  async function manejarEnvio(evento: React.FormEvent) {
    evento.preventDefault()
    setErrorLocal(null)

    if (!especialistaId) {
      setErrorLocal('Selecciona el doctor responsable del plan.')
      return
    }
    if (usarCuotas && (!Number.isInteger(nCuotas) || nCuotas < 0 || nCuotas > 60 || importe <= 0 || (nCuotas > 0 && !(Number(cuotaMensual) > 0)))) {
      setErrorLocal('Indica la cuota inicial y/o una cuota mensual válida, con hasta 60 cuotas.')
      return
    }
    if (darDescuentoRecomendacion && !(precioAjustado && precioCatalogo !== null && importe < precioCatalogo)) {
      setErrorLocal('Para dar el descuento por recomendación, baja el presupuesto por debajo del precio del catálogo.')
      return
    }
    if (precioAjustado && !darDescuentoRecomendacion && motivoPrecio.trim().length < 5) {
      setErrorLocal('El precio acordado es distinto al del catálogo: indica el motivo (mínimo 5 caracteres).')
      return
    }
    if (!nombre.trim() || !Number.isFinite(importe) || importe < 0 ||
        !Number.isInteger(Number(totalSesiones)) || Number(totalSesiones) < 1) {
      setErrorLocal('Indica el tratamiento, un presupuesto válido y el número de sesiones.')
      return
    }
    if (!Number.isFinite(importeAdelanto) || importeAdelanto < 0 || importeAdelanto > importe) {
      setErrorLocal(`El adelanto no puede superar el presupuesto de ${formatSoles(importe || 0)}.`)
      return
    }

    let planPreparado = planCreado
    try {
      let plan = planCreado
      if (!plan) {
        plan = await crearTratamiento.mutateAsync(crearSolicitudPlanSimple({
          pacienteId,
          sedeId: sedeIdLimpia,
          especialistaId,
          nombre,
          costoTotal: importe,
          totalSesiones: Number(totalSesiones),
          observaciones,
          cuotas: usarCuotas
            ? {
                montoInicial: Number(cuotaInicial) || 0,
                montoMensual: Number(cuotaMensual) || 0,
                cantidadMensuales: nCuotas,
                primerVencimiento,
              }
            : undefined,
          precioCatalogo: precioAjustado ? (precioCatalogo ?? undefined) : undefined,
          motivoAjustePrecio: precioAjustado
            ? (darDescuentoRecomendacion && descuentoPendiente
                ? `Descuento por recomendación de ${descuentoPendiente.referidoNombre ?? 'un paciente'}${motivoPrecio.trim() ? `. ${motivoPrecio.trim()}` : ''}`
                : motivoPrecio)
            : undefined,
          procedimientoId: servicioId || undefined,
          piezas: Number.isInteger(nPiezas) && nPiezas >= 1 ? nPiezas : 1,
          costoPorPieza: costoPorPieza.trim() === '' ? undefined : Number(costoPorPieza),
          gravedad: gravedad || undefined,
          otrosCasos: otrosCasos.map((c) => ({ gravedad: c.gravedad || undefined, piezas: Number(c.piezas), costoPorPieza: Number(c.costoPorPieza) })),
        }))
        if (!plan.id) {
          setErrorLocal('El servidor no devolvió el identificador del plan.')
          return
        }
        setPlanCreado(plan)
        planPreparado = plan
      }

      if (darDescuentoRecomendacion && descuentoPendiente) {
        await aplicarDescuento.mutateAsync({ descuentoId: descuentoPendiente.id, tratamientoId: plan.id })
        setDarDescuentoRecomendacion(false)
      }

      if (importeAdelanto > 0) {
        await registrarPago.mutateAsync({
          pacienteId,
          tratamientoId: plan.id,
          especialistaId,
          sedeId: sedeIdLimpia,
          monto: importeAdelanto,
          metodoPago,
          comprobanteTipo: 'RECIBO',
        })
      }

      if (plantillaNueva.trim() && servicioElegido?.id) {
        try {
          await crearPlantilla.mutateAsync({
            nombre: plantillaNueva.trim(),
            procedimientoId: servicioElegido.id,
            servicioNombre: servicioElegido.nombre ?? nombre,
            piezas: Number.isInteger(nPiezas) && nPiezas >= 1 ? nPiezas : 1,
            gravedad: tieneCostoPorGravedad && gravedad ? gravedad : undefined,
            sesiones: Number(totalSesiones),
            notas: observaciones.trim() || undefined,
          })
          toast(`Plantilla «${plantillaNueva.trim()}» guardada`)
        } catch (errorPlantilla) {
          // El plan ya quedó creado: solo se avisa que la plantilla no se guardó.
          toast.error(textoDeError(errorPlantilla, 'El plan se creó, pero no se pudo guardar la plantilla.'))
        }
      }

      alGuardar?.(plan)
      alCerrar()
    } catch (error) {
      const detalle = textoDeError(error, 'No se pudo guardar el plan.')
      setErrorLocal(planCreado || planPreparado
        ? `El plan ya está creado; al reintentar se usará el mismo plan. ${detalle}`
        : detalle)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(valor) => !valor && alCerrar()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Nuevo Plan de Tratamiento</DialogTitle>
          <DialogDescription>
            Presupuesto para {pacienteNombre ?? 'el paciente'}.
          </DialogDescription>
        </DialogHeader>

        {errorLocal && <Alert tono="error" titulo={errorLocal} />}
        {planCreado && (
          <p role="status" className="text-sm text-on-surface-variant">
            Plan creado. Si el adelanto falla, puedes reintentar sin duplicar el plan.
          </p>
        )}

        {planesEnCurso.length > 0 && !planCreado && (
          <section aria-label="Planes en curso" className="rounded-panel border border-brand-gold/40 bg-brand-gold/10 p-3 text-sm">
            <p className="font-semibold text-primary">Este paciente ya tiene planes en curso</p>
            <ul className="mt-1 flex flex-col gap-0.5 text-on-surface-variant">
              {planesEnCurso.map((t) => (
                <li key={t.id}>
                  <strong className="text-on-surface">{t.nombre}</strong> · sesiones {t.sesionesRealizadas ?? 0} de {t.totalSesiones ?? 1}
                  {(t.saldoPendiente ?? 0) > 0 ? ` · saldo ${formatSoles(t.saldoPendiente ?? 0)}` : ''}
                </li>
              ))}
            </ul>
            <p className="mt-1 text-xs text-on-surface-variant">Para continuar uno, agenda una cita con ese plan; así no se duplica. Para anularlo, ciérralo desde la ficha.</p>
          </section>
        )}

        {descuentoPendiente && !planCreado && (
          <section aria-label="Descuento por recomendación" className="rounded-panel border border-primary/30 bg-primary-fixed/40 p-3 text-sm">
            <p className="font-semibold text-primary">
              Este paciente recomendó a {descuentoPendiente.referidoNombre ?? 'un paciente nuevo'}: tiene un descuento por recomendación pendiente.
            </p>
            {['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'].includes(sesion?.usuario.rol ?? '') && (
              <label className="mt-1.5 flex items-center gap-2 text-on-surface">
                <input type="checkbox" className="size-4" checked={darDescuentoRecomendacion} onChange={(e) => setDarDescuentoRecomendacion(e.target.checked)} />
                Dar el descuento en este plan (baja el presupuesto al monto que decidas)
              </label>
            )}
          </section>
        )}

        <form onSubmit={manejarEnvio} className="space-y-4">
          <SelectorPlantilla plantillas={plantillas} deshabilitado={Boolean(planCreado)} alElegir={aplicarPlantilla} />
          <Field label="Tratamiento del catálogo">
            <select
              aria-label="Tratamiento del catálogo"
              value={servicioId}
              onChange={(evento) => elegirServicio(catalogo.find((servicio) => servicio.id === evento.target.value))}
              disabled={Boolean(planCreado)}
              className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
            >
              <option value="">Elige un tratamiento o escribe uno propio</option>
              {catalogo.map((servicio) => (
                <option key={servicio.id} value={servicio.id}>
                  {servicio.nombre}{servicio.precio != null && servicio.precio > 0 ? ` · ${formatSoles(servicio.precio)}` : ''}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Nombre del plan *">
            <Input
              value={nombre}
              onChange={(evento) => setNombre(evento.target.value)}
              required
              disabled={Boolean(planCreado)}
              ref={enfocarAlMontar}
            />
          </Field>

          {!usarCuotas && (
            <div className="grid gap-3 sm:grid-cols-3">
              {/* Solo aparece si este servicio tiene un costo distinto según la gravedad (se define en Administración > Servicios). */}
              {tieneCostoPorGravedad && (
              <Field label="Gravedad del caso" ayuda="Leve, moderado o severo: cambia el costo por pieza.">
                <select
                  aria-label="Gravedad del caso"
                  value={gravedad}
                  onChange={(evento) => elegirGravedad(evento.target.value as '' | 'LEVE' | 'MODERADO' | 'SEVERO')}
                  disabled={Boolean(planCreado)}
                  className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                >
                  <option value="">No aplica</option>
                  <option value="LEVE">Leve</option>
                  <option value="MODERADO">Moderado</option>
                  <option value="SEVERO">Severo</option>
                </select>
              </Field>
              )}
              <Field label="Piezas o unidades">
                <Input
                  type="number"
                  min="1"
                  step="1"
                  inputMode="numeric"
                  value={piezas}
                  onChange={(evento) => {
                    setPiezas(evento.target.value)
                    recalcularTotal(evento.target.value, costoPorPieza)
                  }}
                  disabled={Boolean(planCreado)}
                />
              </Field>
              <Field label="Costo por pieza (S/)">
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  inputMode="decimal"
                  value={costoPorPieza}
                  onChange={(evento) => {
                    setCostoPorPieza(evento.target.value)
                    recalcularTotal(piezas, evento.target.value)
                  }}
                  disabled={Boolean(planCreado)}
                />
              </Field>
              {otrosCasos.map((caso, i) => (
                <div key={i} className="grid items-end gap-3 sm:col-span-3 sm:grid-cols-[1fr_1fr_1fr_auto]" role="group" aria-label={`Caso ${i + 2}`}>
                  <Field label={`Caso ${i + 2}: gravedad`}>
                    <select
                      aria-label={`Gravedad del caso ${i + 2}`}
                      value={caso.gravedad}
                      onChange={(evento) => elegirGravedadDeCaso(i, evento.target.value as '' | 'LEVE' | 'MODERADO' | 'SEVERO')}
                      disabled={Boolean(planCreado)}
                      className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
                    >
                      <option value="">No aplica</option>
                      <option value="LEVE">Leve</option>
                      <option value="MODERADO">Moderado</option>
                      <option value="SEVERO">Severo</option>
                    </select>
                  </Field>
                  <Field label="Piezas o unidades">
                    <Input
                      type="number"
                      min="1"
                      step="1"
                      inputMode="numeric"
                      aria-label={`Piezas del caso ${i + 2}`}
                      value={caso.piezas}
                      onChange={(evento) => cambiarCaso(i, { piezas: evento.target.value })}
                      disabled={Boolean(planCreado)}
                    />
                  </Field>
                  <Field label="Costo por pieza (S/)">
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      inputMode="decimal"
                      aria-label={`Costo por pieza del caso ${i + 2}`}
                      value={caso.costoPorPieza}
                      onChange={(evento) => cambiarCaso(i, { costoPorPieza: evento.target.value })}
                      disabled={Boolean(planCreado)}
                    />
                  </Field>
                  <Button type="button" variant="secundario" size="sm" onClick={() => quitarCaso(i)} disabled={Boolean(planCreado)} aria-label={`Quitar el caso ${i + 2}`}>
                    <Trash2 aria-hidden />
                  </Button>
                </div>
              ))}
              {tieneCostoPorGravedad && !planCreado && (
                <div className="sm:col-span-3">
                  <Button type="button" variant="secundario" size="sm" onClick={agregarCaso}>
                    <Plus aria-hidden />
                    Agregar otro caso de este tratamiento
                  </Button>
                </div>
              )}
            </div>
          )}

          <Field
            label="Doctor responsable *"
            ayuda={especialistaSeleccionado && (
              <span>{sillon ? `Sillón ${sillon}` : 'Sillón no asignado'} · {especialistaSeleccionado.nombre}</span>
            )}
          >
            <select
              aria-label="Doctor responsable"
              value={especialistaId}
              onChange={(evento) => setEspecialistaId(evento.target.value)}
              disabled={Boolean(planCreado) || esDoctor}
              required
              className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
            >
              {!esDoctor && <option value="">Selecciona un doctor</option>}
              {opcionesDoctor.map((especialista) => (
                <option key={especialista.id} value={especialista.id}>
                  {especialista.nombre}
                </option>
              ))}
            </select>
          </Field>

          <label className="flex items-center gap-2 text-sm font-medium text-on-surface">
            <input
              type="checkbox"
              checked={usarCuotas}
              onChange={(evento) => setUsarCuotas(evento.target.checked)}
              disabled={Boolean(planCreado)}
            />
            Pagar en cuotas (ortodoncia)
          </label>

          {usarCuotas && (
            <div className="grid gap-3 sm:grid-cols-4">
              <Field label="Cuota inicial (S/)">
                <Input type="number" step="0.01" min="0" value={cuotaInicial} onChange={(evento) => setCuotaInicial(evento.target.value)} disabled={Boolean(planCreado)} />
              </Field>
              <Field label="Cuota mensual (S/)">
                <Input type="number" step="0.01" min="0" value={cuotaMensual} onChange={(evento) => setCuotaMensual(evento.target.value)} disabled={Boolean(planCreado)} />
              </Field>
              <Field label="N.º de cuotas mensuales">
                <Input type="number" min="0" max="60" step="1" value={cuotasMensuales} onChange={(evento) => setCuotasMensuales(evento.target.value)} disabled={Boolean(planCreado)} />
              </Field>
              <Field label="Primer vencimiento">
                <Input type="date" value={primerVencimiento} onChange={(evento) => setPrimerVencimiento(evento.target.value)} required disabled={Boolean(planCreado)} />
              </Field>
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={usarCuotas ? 'Presupuesto total (suma de las cuotas)' : 'Presupuesto total (S/) *'}>
              <Input
                type="number"
                step="0.01"
                min="0"
                value={usarCuotas ? (importe > 0 ? importe.toFixed(2) : '') : costoTotal}
                onChange={(evento) => setCostoTotal(evento.target.value)}
                required
                readOnly={usarCuotas}
                disabled={Boolean(planCreado)}
              />
            </Field>
            <Field label="Número de sesiones estimadas *">
              <Input
                type="number"
                min="1"
                step="1"
                value={totalSesiones}
                onChange={(evento) => setTotalSesiones(evento.target.value)}
                required
                disabled={Boolean(planCreado)}
              />
            </Field>
          </div>

          {precioAjustado && !darDescuentoRecomendacion && (
            <Field label={`Motivo del precio acordado * (catálogo: ${formatSoles(precioCatalogo ?? 0)})`}>
              <Input
                value={motivoPrecio}
                onChange={(evento) => setMotivoPrecio(evento.target.value)}
                placeholder="Ej. pieza con mucha pérdida de estructura"
                disabled={Boolean(planCreado)}
              />
            </Field>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Adelanto hoy (S/, opcional)">
              <Input
                type="number"
                step="0.01"
                min="0"
                max={Number.isFinite(importe) ? importe : undefined}
                value={adelanto}
                onChange={(evento) => setAdelanto(evento.target.value)}
                disabled={Boolean(planCreado)}
              />
            </Field>
            <Field label="Método del adelanto">
              <select
                aria-label="Método del adelanto"
                value={metodoPago}
                onChange={(evento) => setMetodoPago(evento.target.value as MetodoPago)}
                disabled={Boolean(planCreado)}
                className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
              >
                {METODOS_PAGO.map((m) => (
                  <option key={m.valor} value={m.valor}>{m.nombre}</option>
                ))}
              </select>
            </Field>
          </div>

          {servicioElegido && !usarCuotas && !planCreado && <GuardarComoPlantilla valor={plantillaNueva} alCambiar={setPlantillaNueva} />}

          <Field label="Notas (opcional)">
            <textarea
              rows={2}
              value={observaciones}
              onChange={(evento) => setObservaciones(evento.target.value)}
              disabled={Boolean(planCreado)}
              className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
            />
          </Field>

          <DialogFooter>
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={pendiente}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={pendiente}>
              {pendiente ? 'Guardando…' : planCreado ? 'Reintentar adelanto' : 'Crear plan'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
