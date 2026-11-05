import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Coins,
  CreditCard,
  MapPin,
  MessageCircle,
  Plus,
  RefreshCw,
  Scan,
  Search,
  Users,
} from 'lucide-react'
import { Badge, Button, Input } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { RotuloMostrando } from '../layout/RotuloMostrando'
import { api } from '@/shared/http'
import { ModalLiquidarComision } from './ModalLiquidarComision'
import { EstadoCuentaDerivador } from './EstadoCuentaDerivador'
import { ModalLiquidarLote } from './ModalLiquidarLote'
import { PanelComisionesFallidas } from './PanelComisionesFallidas'
import { ModalNuevaComision } from './ModalNuevaComision'
import { ModalNuevoDerivador } from './ModalNuevoDerivador'
import type {
  ComisionRadiologicaCreateRequestDTO,
  ComisionRadiologicaDTO,
  ComisionRadiologicaLiquidarRequestDTO,
  DoctorDerivadorDTO,
  DoctorDerivadorRequestDTO,
  ResumenComisionesDTO,
} from './types'

export function RadiologiaPage() {
  const sesion = useSesion()
  const rol = sesion?.usuario.rol
  const esAdminGeneral = rol === 'ADMIN_GENERAL'
  const esRecepcion = rol === 'RECEPCION'

  const [comisiones, setComisiones] = useState<ComisionRadiologicaDTO[]>([])
  const [doctores, setDoctores] = useState<DoctorDerivadorDTO[]>([])
  const [resumen, setResumen] = useState<ResumenComisionesDTO | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filtros: recepción no ve comisiones, arranca en doctores
  const [pestanaActiva, setPestanaActiva] = useState<'comisiones' | 'doctores'>(esRecepcion ? 'doctores' : 'comisiones')
  const [filtroEstado, setFiltroEstado] = useState<'TODOS' | 'PENDIENTE' | 'LIQUIDADO'>('TODOS')
  const [busqueda, setBusqueda] = useState('')

  // Modales
  const [modalNuevaComision, setModalNuevaComision] = useState(false)
  const [modalNuevoDerivador, setModalNuevoDerivador] = useState(false)
  const [doctorAEditar, setDoctorAEditar] = useState<DoctorDerivadorDTO | null>(null)
  const [comisionALiquidar, setComisionALiquidar] = useState<ComisionRadiologicaDTO | null>(null)
  const [seleccion, setSeleccion] = useState<string[]>([])
  const [modalLote, setModalLote] = useState(false)
  const [doctorEstadoCuenta, setDoctorEstadoCuenta] = useState<{ id: string; nombre: string } | null>(null)

  // Trae los datos sin tocar el estado de carga al empezar (arranca en "cargando", sin error).
  const traerDatos = useCallback(
    () =>
      Promise.all([
        api.get<ComisionRadiologicaDTO[]>('/radiologia/comisiones').catch(() => []),
        api.get<DoctorDerivadorDTO[]>('/radiologia/derivadores').catch(() => []),
        api.get<ResumenComisionesDTO>('/radiologia/comisiones/resumen').catch(() => null),
      ])
        .then(([resComisiones, resDoctores, resMetricas]) => {
          setComisiones(resComisiones ?? [])
          setDoctores(resDoctores ?? [])
          setResumen(resMetricas)
        })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : 'Error al cargar datos de radiología')
        })
        .finally(() => {
          setCargando(false)
        }),
    [],
  )

  // Recarga explícita tras guardar o liquidar: vuelve a mostrar el estado de carga.
  const cargarDatos = async () => {
    setCargando(true)
    setError(null)
    await traerDatos()
  }

  useEffect(() => {
    void traerDatos()
  }, [traerDatos])

  // Guardar nuevo doctor
  const handleGuardarDoctor = async (req: DoctorDerivadorRequestDTO) => {
    if (doctorAEditar) {
      await api.put(`/radiologia/derivadores/${doctorAEditar.id}`, req)
    } else {
      await api.post('/radiologia/derivadores', req)
    }
    await cargarDatos()
  }

  // Guardar nueva comisión
  const handleGuardarComision = async (req: ComisionRadiologicaCreateRequestDTO) => {
    await api.post('/radiologia/comisiones', req)
    await cargarDatos()
  }

  // Liquidar comisión
  const handleLiquidarComision = async (id: string, req: ComisionRadiologicaLiquidarRequestDTO) => {
    await api.patch(`/radiologia/comisiones/${id}/liquidar`, req)
    await cargarDatos()
  }

  const seleccionadas = comisiones.filter((c) => seleccion.includes(c.id))
  const alternarSeleccion = (id: string) =>
    setSeleccion((actual) => (actual.includes(id) ? actual.filter((x) => x !== id) : [...actual, id]))

  // Comisiones filtradas
  const comisionesFiltradas = useMemo(() => {
    return comisiones.filter((c) => {
      const coincideEstado = filtroEstado === 'TODOS' || c.estado === filtroEstado
      const texto = busqueda.toLowerCase().trim()
      const coincideTexto =
        !texto ||
        c.pacienteNombre.toLowerCase().includes(texto) ||
        c.doctorDerivadorNombre.toLowerCase().includes(texto) ||
        (c.tipoEstudio && c.tipoEstudio.toLowerCase().includes(texto))
      return coincideEstado && coincideTexto
    })
  }, [comisiones, filtroEstado, busqueda])

  // Doctores filtrados
  const doctoresFiltrados = useMemo(() => {
    return doctores.filter((d) => {
      const texto = busqueda.toLowerCase().trim()
      return (
        !texto ||
        d.nombre.toLowerCase().includes(texto) ||
        (d.cop && d.cop.toLowerCase().includes(texto)) ||
        (d.clinicaOrigen && d.clinicaOrigen.toLowerCase().includes(texto))
      )
    })
  }, [doctores, busqueda])

  return (
    <div className="flex flex-col gap-6">
      {/* ─── Encabezado Oficial Villamax ─── */}
      <div className="flex flex-col gap-3 rounded-panel border border-outline-variant/60 bg-surface-container-low p-6 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-xl bg-tertiary text-on-tertiary shadow-sm">
              <Scan className="size-5" />
            </span>
            <div className="flex items-center gap-2">
              <h1 className="font-display text-2xl font-bold tracking-tight text-on-surface">
                Villamax · Centro Radiológico & Tomografía 3D
              </h1>
              <RotuloMostrando />
            </div>
          </div>
          <p className="flex items-center gap-1.5 text-xs text-on-surface-variant">
            <MapPin className="size-3.5 text-tertiary" />
            Jr. Ricardo Palma 411, Chilca (al costado de la clínica odontológica) • Convenios B2B y Derivaciones
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="secundario"
            size="sm"
            onClick={() => {
              setDoctorAEditar(null)
              setModalNuevoDerivador(true)
            }}
          >
            <Plus className="mr-1 size-4" />
            + Doctor Derivador
          </Button>
          {esAdminGeneral && (
            <Button
              variant="accion"
              size="sm"
              onClick={() => setModalNuevaComision(true)}
              disabled={doctores.length === 0}
            >
              <Plus className="mr-1 size-4" />
              + Registrar Comisión
            </Button>
          )}
        </div>
      </div>

      {esAdminGeneral && <PanelComisionesFallidas onReintentado={cargarDatos} />}

      {/* ─── KPI Cards Gerenciales ─── */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {esAdminGeneral ? (
          <>
            <div className="flex flex-col gap-1 rounded-card border border-warning/30 bg-surface-container-lowest p-5 shadow-xs">
              <span className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant uppercase tracking-wider">
                <Clock className="size-4 text-warning" />
                Comisiones Pendientes
              </span>
              <p className="font-display text-2xl font-bold text-warning">
                S/ {resumen ? resumen.totalMontoPendiente.toFixed(2) : '0.00'}
              </p>
              <span className="text-xs text-on-surface-variant">
                {resumen ? `${resumen.cantidadPendientes} estudio(s) por pagar` : '0 pendientes'}
              </span>
            </div>

            <div className="flex flex-col gap-1 rounded-card border border-primary/20 bg-surface-container-lowest p-5 shadow-xs">
              <span className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant uppercase tracking-wider">
                <CheckCircle2 className="size-4 text-primary" />
                Comisiones Liquidadas
              </span>
              <p className="font-display text-2xl font-bold text-primary">
                S/ {resumen ? resumen.totalMontoLiquidado.toFixed(2) : '0.00'}
              </p>
              <span className="text-xs text-on-surface-variant">
                {resumen ? `${resumen.cantidadLiquidados} comisiones pagadas vía Yape/Transf.` : '0 liquidadas'}
              </span>
            </div>
          </>
        ) : (
          <div className="flex flex-col gap-1 rounded-card border border-outline-variant/50 bg-surface-container-lowest p-5 shadow-xs">
            <span className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant uppercase tracking-wider">
              <Scan className="size-4 text-tertiary" />
              Estudios Derivados
            </span>
            <p className="font-display text-2xl font-bold text-on-surface">
              {comisiones.length}
            </p>
            <span className="text-xs text-on-surface-variant">
              Total de registros de estudios radiológicos
            </span>
          </div>
        )}

        <div className="flex flex-col gap-1 rounded-card border border-outline-variant/50 bg-surface-container-lowest p-5 shadow-xs">
          <span className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant uppercase tracking-wider">
            <Users className="size-4 text-tertiary" />
            Doctores con Convenio
          </span>
          <p className="font-display text-2xl font-bold text-on-surface">
            {resumen ? resumen.totalDoctoresDerivadoresActivos : doctores.length}
          </p>
          <span className="text-xs text-on-surface-variant">
            Odontólogos y clínicas en Huancayo, Chilca y El Tambo
          </span>
        </div>

        <div className="flex flex-col gap-1 rounded-card border border-outline-variant/50 bg-surface-container-lowest p-5 shadow-xs">
          <span className="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant uppercase tracking-wider">
            <Coins className="size-4 text-secondary" />
            Cobro en Villamax
          </span>
          <p className="font-display text-base font-bold text-on-surface">
            100% en Recepción Chilca
          </p>
          <span className="text-xs text-on-surface-variant">
            El paciente paga en local; la comisión se liquida al derivador
          </span>
        </div>
      </div>

      {/* ─── Pestañas y Filtros ─── */}
      <div className="flex flex-col gap-4 rounded-card border border-outline-variant/60 bg-surface-container-lowest p-5">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant/40 pb-4">
          <div className="flex items-center gap-2">
            {!esRecepcion && (
              <button
                type="button"
                onClick={() => setPestanaActiva('comisiones')}
                className={`rounded-panel px-4 py-2 text-sm font-bold transition-colors ${
                  pestanaActiva === 'comisiones'
                    ? 'bg-tertiary text-on-tertiary shadow-sm'
                    : 'text-on-surface-variant hover:bg-surface-container-high'
                }`}
              >
                Comisiones y Liquidaciones ({comisiones.length})
              </button>
            )}
            <button
              type="button"
              onClick={() => setPestanaActiva('doctores')}
              className={`rounded-panel px-4 py-2 text-sm font-bold transition-colors ${
                pestanaActiva === 'doctores'
                  ? 'bg-tertiary text-on-tertiary shadow-sm'
                  : 'text-on-surface-variant hover:bg-surface-container-high'
              }`}
            >
              Doctores Derivadores ({doctores.length})
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[220px]">
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-on-surface-variant" />
              <Input
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                placeholder={
                  pestanaActiva === 'comisiones'
                    ? 'Buscar paciente o doctor...'
                    : 'Buscar doctor o clínica...'
                }
                className="pl-9 text-xs"
              />
            </div>

            {pestanaActiva === 'comisiones' && (
              <div className="flex items-center gap-1 rounded-control border border-outline-variant/60 bg-surface-container-lowest p-1 text-xs">
                {(['TODOS', 'PENDIENTE', 'LIQUIDADO'] as const).map((estado) => (
                  <button
                    key={estado}
                    type="button"
                    onClick={() => setFiltroEstado(estado)}
                    className={`rounded-control px-2.5 py-1 font-bold transition-colors ${
                      filtroEstado === estado
                        ? 'bg-surface-container-high text-on-surface shadow-xs'
                        : 'text-on-surface-variant hover:text-on-surface'
                    }`}
                  >
                    {estado === 'TODOS' ? 'Todos' : estado === 'PENDIENTE' ? 'Pendientes' : 'Liquidados'}
                  </button>
                ))}
              </div>
            )}

            <Button variant="secundario" size="sm" onClick={() => void cargarDatos()} disabled={cargando}>
              <RefreshCw className={`size-3.5 ${cargando ? 'animate-spin' : ''}`} />
            </Button>
          </div>
        </div>

        {/* ─── Contenido Pestaña 1: Comisiones ─── */}
        {pestanaActiva === 'comisiones' && (
          <div className="overflow-x-auto">
            {error && (
              <div className="mb-4 flex items-center gap-2 rounded-panel bg-error-container/20 p-3 text-xs text-error">
                <AlertCircle className="size-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {cargando && comisiones.length === 0 ? (
              <div className="py-12 text-center text-sm text-on-surface-variant">
                Cargando comisiones de radiología...
              </div>
            ) : comisionesFiltradas.length === 0 ? (
              <div className="py-12 text-center text-sm text-on-surface-variant">
                No se encontraron registros de comisiones con los filtros aplicados.
              </div>
            ) : (
              <>
              {esAdminGeneral && seleccionadas.length > 0 && (
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-panel bg-surface-container-low p-3 text-sm">
                  <span>
                    {seleccionadas.length} {seleccionadas.length === 1 ? 'comisión elegida' : 'comisiones elegidas'} · S/{' '}
                    {seleccionadas.reduce((suma, c) => suma + c.montoComision, 0).toFixed(2)}
                  </span>
                  <div className="flex gap-2">
                    <Button size="sm" variant="fantasma" onClick={() => setSeleccion([])}>
                      Quitar selección
                    </Button>
                    <Button size="sm" variant="accion" onClick={() => setModalLote(true)}>
                      Liquidar elegidas
                    </Button>
                  </div>
                </div>
              )}
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-outline-variant/40 text-on-surface-variant">
                    {esAdminGeneral && <th className="w-8 pb-3"><span className="sr-only">Elegir</span></th>}
                    <th className="pb-3 font-bold">Fecha</th>
                    <th className="pb-3 font-bold">Paciente</th>
                    <th className="pb-3 font-bold">Doctor Derivador</th>
                    <th className="pb-3 font-bold">Estudio Radiológico</th>
                    <th className="pb-3 font-bold text-right">Cobrado (S/)</th>
                    {esAdminGeneral && <th className="pb-3 font-bold text-right">Comisión (S/)</th>}
                    <th className="pb-3 font-bold text-center">Estado</th>
                    <th className="pb-3 font-bold text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {comisionesFiltradas.map((com) => {
                    const esPendiente = com.estado === 'PENDIENTE'
                    const telefonoLimpio = com.doctorDerivadorTelefono?.replace(/\D/g, '') ?? ''
                    const telefonoWa = telefonoLimpio.startsWith('51') ? telefonoLimpio : `51${telefonoLimpio}`
                    const mensajeWhatsApp = encodeURIComponent(
                      `Estimado(a) ${com.doctorDerivadorNombre}, Villamax Chilca le informa que se ha liquidado su comisión de S/ ${com.montoComision.toFixed(2)} por el estudio (${com.tipoEstudio}) del paciente ${com.pacienteNombre}. ¡Gracias por derivar a Villamax!`
                    )
                    const urlWhatsApp = `https://wa.me/${telefonoWa}?text=${mensajeWhatsApp}`

                    return (
                      <tr key={com.id} className="hover:bg-surface-container-low/50">
                        {esAdminGeneral && (
                          <td className="py-3">
                            {esPendiente && (
                              <input
                                type="checkbox"
                                aria-label={`Elegir comisión de ${com.pacienteNombre}`}
                                checked={seleccion.includes(com.id)}
                                onChange={() => alternarSeleccion(com.id)}
                              />
                            )}
                          </td>
                        )}
                        <td className="py-3 font-mono text-xs text-on-surface-variant">
                          {com.fechaEmision ? new Date(com.fechaEmision).toLocaleDateString('es-PE') : '-'}
                        </td>
                        <td className="py-3 font-bold text-on-surface">{com.pacienteNombre}</td>
                        <td className="py-3">
                          <div className="flex flex-col">
                            <span className="font-bold text-on-surface">{com.doctorDerivadorNombre}</span>
                            <span className="text-xs text-on-surface-variant">
                              {com.doctorDerivadorClinica ?? 'Consultorio externo'}
                            </span>
                          </div>
                        </td>
                        <td className="py-3">
                          <Badge tono="villamax">{com.tipoEstudio}{(com.cantidad ?? 1) > 1 ? ` × ${com.cantidad}` : ''}</Badge>
                          {com.esEstudiante && <Badge tono="primario" className="ml-1">Estudiante</Badge>}
                        </td>
                        <td className="py-3 text-right font-mono font-semibold text-on-surface">
                          S/ {com.precioCobrado.toFixed(2)}
                        </td>
                        {esAdminGeneral && (
                          <td className="py-3 text-right font-mono font-bold text-tertiary">
                            S/ {com.montoComision.toFixed(2)}
                          </td>
                        )}
                        <td className="py-3 text-center">
                          {esPendiente ? (
                            <Badge tono="accion">Pendiente</Badge>
                          ) : (
                            <Badge tono="primario">Liquidado ({com.metodoPago ?? 'YAPE'})</Badge>
                          )}
                        </td>
                        <td className="py-3 text-center">
                          <div className="flex items-center justify-center gap-1.5">
                            {esPendiente ? (
                              esAdminGeneral ? (
                                <Button
                                  size="sm"
                                  variant="accion"
                                  onClick={() => setComisionALiquidar(com)}
                                  className="text-xs font-bold"
                                >
                                  <CreditCard className="mr-1 size-3.5" />
                                  Liquidar
                                </Button>
                              ) : (
                                <span className="text-xs text-on-surface-variant">Pendiente</span>
                              )
                            ) : (
                              com.doctorDerivadorTelefono && (
                                <a
                                  href={urlWhatsApp}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="inline-flex items-center gap-1 rounded-control bg-secondary/15 px-2.5 py-1 text-xs font-bold text-secondary hover:bg-secondary/25"
                                  title="Avisar por WhatsApp"
                                >
                                  <MessageCircle className="size-3.5" />
                                  Avisar WA
                                </a>
                              )
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
              </>
            )}
          </div>
        )}

        {/* ─── Contenido Pestaña 2: Doctores Derivadores ─── */}
        {pestanaActiva === 'doctores' && (
          <div className="overflow-x-auto">
            {cargando && doctores.length === 0 ? (
              <div className="py-12 text-center text-sm text-on-surface-variant">
                Cargando directorio de doctores derivadores...
              </div>
            ) : doctoresFiltrados.length === 0 ? (
              <div className="py-12 text-center text-sm text-on-surface-variant">
                No se encontraron doctores derivadores. Puedes registrar uno con el botón superior.
              </div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-outline-variant/40 text-on-surface-variant">
                    <th className="pb-3 font-bold">Doctor (COP)</th>
                    <th className="pb-3 font-bold">Clínica / Procedencia</th>
                    <th className="pb-3 font-bold">WhatsApp / Contacto</th>
                    <th className="pb-3 font-bold">Cuenta / Billetera Yape</th>
                    <th className="pb-3 font-bold text-right">Pendiente por Pagar (S/)</th>
                    <th className="pb-3 font-bold text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/20">
                  {doctoresFiltrados.map((doc) => {
                    const tienePendiente = (doc.totalComisionesPendientes ?? 0) > 0
                    const telefonoLimpio = doc.telefono?.replace(/\D/g, '') ?? ''
                    const telefonoWa = telefonoLimpio.startsWith('51') ? telefonoLimpio : `51${telefonoLimpio}`

                    return (
                      <tr key={doc.id} className="hover:bg-surface-container-low/50">
                        <td className="py-3">
                          <div className="flex flex-col">
                            <span className="font-bold text-on-surface">{doc.nombre}</span>
                            <span className="text-xs text-on-surface-variant font-mono">{doc.cop ?? 'Sin COP'}</span>
                          </div>
                        </td>
                        <td className="py-3 text-on-surface">
                          {doc.clinicaOrigen ?? 'Consultorio independiente'}
                        </td>
                        <td className="py-3">
                          {doc.telefono ? (
                            <a
                              href={`https://wa.me/${telefonoWa}`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="inline-flex items-center gap-1 font-mono text-secondary hover:underline"
                            >
                              <MessageCircle className="size-3.5" />
                              {doc.telefono}
                            </a>
                          ) : (
                            <span className="text-on-surface-variant">No registrado</span>
                          )}
                        </td>
                        <td className="py-3">
                          <div className="flex flex-col">
                            <span className="font-bold text-on-surface">{doc.banco ?? 'Yape / BCP'}</span>
                            <span className="font-mono text-xs text-tertiary">
                              {doc.numeroCuentaOYape ?? '-'}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 text-right font-mono font-bold">
                          {tienePendiente ? (
                            <span className="text-warning">S/ {doc.totalComisionesPendientes?.toFixed(2)}</span>
                          ) : (
                            <span className="text-on-surface-variant">S/ 0.00</span>
                          )}
                        </td>
                        <td className="py-3 text-center">
                          <Button
                            size="sm"
                            variant="secundario"
                            onClick={() => {
                              setDoctorAEditar(doc)
                              setModalNuevoDerivador(true)
                            }}
                            className="text-xs"
                          >
                            Editar
                          </Button>
                          {esAdminGeneral && (
                            <Button
                              size="sm"
                              variant="fantasma"
                              onClick={() => setDoctorEstadoCuenta({ id: doc.id, nombre: doc.nombre })}
                              className="ml-1 text-xs"
                            >
                              Estado de cuenta
                            </Button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>

      {/* ─── Modales ─── */}
      <ModalNuevoDerivador
        abierto={modalNuevoDerivador}
        onCerrar={() => {
          setModalNuevoDerivador(false)
          setDoctorAEditar(null)
        }}
        onGuardar={handleGuardarDoctor}
        doctorAEditar={doctorAEditar}
      />

      <ModalNuevaComision
        abierto={modalNuevaComision}
        onCerrar={() => setModalNuevaComision(false)}
        doctores={doctores}
        onGuardar={handleGuardarComision}
      />

      <ModalLiquidarLote
        abierto={modalLote}
        comisiones={seleccionadas}
        onCerrar={() => setModalLote(false)}
        onLiquidado={async () => {
          setSeleccion([])
          await cargarDatos()
        }}
      />

      <EstadoCuentaDerivador abierto={Boolean(doctorEstadoCuenta)} doctor={doctorEstadoCuenta} onCerrar={() => setDoctorEstadoCuenta(null)} />

      <ModalLiquidarComision
        abierto={Boolean(comisionALiquidar)}
        onCerrar={() => setComisionALiquidar(null)}
        comision={comisionALiquidar}
        onLiquidar={handleLiquidarComision}
      />
    </div>
  )
}
