/** Flujo corto: enviado al laboratorio → en prueba con el paciente (1, 2, 3…) → finalizado. */
export type EstadoOrden = 'ENVIADO' | 'EN_PRUEBA' | 'FINALIZADO' | 'CANCELADO'

export type ResultadoPrueba = 'NECESITA_AJUSTE' | 'CORRECTO'

/** Una prueba del trabajo con el paciente: prueba 1, prueba 2… hasta que queda correcto. */
export interface PruebaLaboratorio {
  id: string
  numero: number
  fecha: string
  resultado: ResultadoPrueba
  notas?: string | null
  registradoPor?: string | null
}

export interface RegistrarPruebaPayload {
  resultado: ResultadoPrueba
  notas?: string
  fecha?: string
  nuevaFechaEntrega?: string
}

export type EstadoPagoLaboratorio = 'PENDIENTE' | 'PARCIAL' | 'PAGADO'

export interface LaboratorioExterno {
  id: string
  nombre: string
  contacto?: string | null
  telefono?: string | null
  email?: string | null
  direccion?: string | null
  tiempoEntregaPromedioDias: number
  tiempo_entrega_promedio_dias?: number
  activo: boolean
  creadoEn?: string
  actualizadoEn?: string
}

export interface OrdenLaboratorioHistorial {
  id: number
  ordenId: string
  orden_id?: string
  estadoAnterior?: string | null
  estado_anterior?: string | null
  estadoNuevo: EstadoOrden
  estado_nuevo?: EstadoOrden
  usuarioNombre: string
  usuario_nombre?: string
  usuarioRol: string
  usuario_rol?: string
  motivo?: string | null
  creadoEn: string
  creado_en?: string
}

export interface OrdenLaboratorio {
  id: string
  codigo: string
  pacienteId: string
  paciente_id?: string
  pacienteNombre: string
  paciente_nombre?: string
  pacienteDni?: string | null
  paciente_dni?: string | null
  especialistaId: string
  especialista_id?: string
  especialistaNombre: string
  especialista_nombre?: string
  sedeId: string
  sede_id?: string
  laboratorioId: string
  laboratorio_id?: string
  laboratorioNombre: string
  laboratorio_nombre?: string
  tipoTrabajo: string
  tipo_trabajo?: string
  colorTono?: string | null
  color_tono?: string | null
  color?: string | null
  material?: string | null
  provisional?: boolean
  piezasDentales?: string | null
  piezas_dentales?: string | null
  estado: EstadoOrden
  /** Pruebas con el paciente, de la 1 en adelante. */
  pruebas?: PruebaLaboratorio[]
  fechaSolicitud: string
  fecha_solicitud?: string
  fechaEnvioLaboratorio?: string | null
  fecha_envio_laboratorio?: string | null
  fechaEstimadaEntrega?: string | null
  fecha_estimada_entrega?: string | null
  fechaRecepcionClinica?: string | null
  fecha_recepcion_clinica?: string | null
  fechaInstalacion?: string | null
  fecha_instalacion?: string | null
  costoLaboratorio?: number | null
  costo_laboratorio?: number | null
  precioPaciente?: number | null
  precio_paciente?: number | null
  estadoPago?: EstadoPagoLaboratorio | null
  montoPagado?: number | null
  saldoLaboratorio?: number | null
  ganancia?: number | null
  guiaRemision?: string | null
  guia_remision?: string | null
  indicacionesTecnicas?: string | null
  indicaciones_tecnicas?: string | null
  observaciones?: string | null
  registradoPor?: string | null
  registrado_por?: string | null
  creadoEn: string
  creado_en?: string
  actualizadoEn?: string
  actualizado_en?: string
  historial?: OrdenLaboratorioHistorial[]
}

export interface FiltrosLaboratorio {
  sedeId?: string
  estado?: EstadoOrden | ''
  pacienteId?: string
  especialistaId?: string
  termino?: string
}

export interface CrearOrdenPayload {
  pacienteId: string
  especialistaId: string
  sedeId: string
  laboratorioId: string
  tipoTrabajo: string
  colorTono?: string
  color?: string
  material?: string
  provisional?: boolean
  piezasDentales?: string
  fechaEstimadaEntrega?: string
  costoLaboratorio?: number
  precioPaciente?: number
  guiaRemision?: string
  indicacionesTecnicas?: string
  observaciones?: string
}

export interface ActualizarOrdenPayload {
  laboratorioId: string
  tipoTrabajo: string
  colorTono?: string
  color?: string
  material?: string
  provisional?: boolean
  piezasDentales?: string
  fechaEstimadaEntrega?: string
  costoLaboratorio?: number
  precioPaciente?: number
  guiaRemision?: string
  indicacionesTecnicas?: string
  observaciones?: string
}

export interface CambioEstadoPayload {
  nuevoEstado: EstadoOrden
  motivo?: string
  guiaRemision?: string
}

export interface GuardarLaboratorioPayload {
  nombre: string
  contacto?: string
  telefono?: string
  email?: string
  direccion?: string
  tiempoEntregaPromedioDias?: number
  activo?: boolean
}

export interface PagoLaboratorio {
  id: string
  ordenId: string
  laboratorioId: string
  sedeId: string
  monto: number
  metodoPago: 'EFECTIVO' | 'YAPE' | 'PLIN'
  numeroOperacion?: string | null
  pagadoEn: string
  cajaSesionId?: string | null
  registradoPor?: string | null
  notas?: string | null
  anulado?: boolean
  anuladoEn?: string | null
  anuladoPor?: string | null
  motivoAnulacion?: string | null
}

export interface RegistrarPagoLaboratorioPayload {
  monto: number
  metodoPago: 'EFECTIVO' | 'YAPE' | 'PLIN'
  numeroOperacion?: string
  notas?: string
}

export interface AnularPagoLaboratorioPayload {
  motivoAnulacion: string
}

export interface OrdenLaboratorioCuenta {
  ordenId: string
  codigo: string
  sedeId: string
  costoLaboratorio?: number | null
  montoPagado: number
  saldoLaboratorio?: number | null
  estadoPago?: EstadoPagoLaboratorio | null
}

export interface LaboratorioCuenta {
  laboratorioId: string
  laboratorioNombre: string
  totalFacturado: number
  totalPagado: number
  saldoPorPagar: number
  ordenes: OrdenLaboratorioCuenta[]
}
