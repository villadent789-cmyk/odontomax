export interface FiltrosReportes {
  sedeId?: string
  desde?: string
  hasta?: string
  unidadNegocio?: 'todas' | 'clinica' | 'radiologia'
  doctorId?: string
}

export interface IngresoDiaDTO {
  fecha: string
  ingresos: number
  gastos: number
  balance: number
}

export interface IngresoMetodoDTO {
  metodo: string
  total: number
  cantidad: number
}

export interface IngresoDoctorDTO {
  doctorId: string
  doctorNombre: string
  totalGenerado: number
  cantidadTratamientos: number
}

export interface IngresoTratamientoDTO {
  procedimientoNombre: string
  totalIngresos: number
  cantidadRealizados: number
}

export interface IngresoSedeDTO {
  sedeId: string
  sedeNombre: string
  totalIngresos: number
  totalGastos: number
  balance: number
}

export interface GastoCategoriaDTO {
  categoria: string
  total: number
}

export interface ReporteFinancieroDTO {
  sedeId?: string | null
  sedeNombre: string
  fechaInicio: string
  fechaFin: string
  totalIngresos: number
  totalGastos: number
  balanceNeto: number
  totalEfectivo: number
  totalYape: number
  totalTarjeta: number
  totalTransferencia: number
  cuentasPorCobrar: number
  tratamientosConSaldoPendiente: number
  ingresosPorDia: IngresoDiaDTO[]
  ingresosPorMetodo: IngresoMetodoDTO[]
  ingresosPorDoctor: IngresoDoctorDTO[]
  ingresosPorTratamiento: IngresoTratamientoDTO[]
  ingresosPorSede: IngresoSedeDTO[]
  gastosPorCategoria?: GastoCategoriaDTO[]
}

export interface PacienteDeudorDTO {
  pacienteId: string
  pacienteNombre: string
  dni: string
  telefono?: string | null
  sedeId: string
  sedeNombre: string
  totalTratamientos: number
  totalAbonado: number
  saldoPendiente: number
  tratamientosPendientes: number
}

export interface ReporteDeudoresDTO {
  sedeId?: string | null
  sedeNombre: string
  deudaTotalAcumulada: number
  totalPacientesDeudores: number
  pacientes: PacienteDeudorDTO[]
}

export interface CitasPorDiaDTO {
  fecha: string
  programadas: number
  atendidas: number
  canceladas: number
  noAsistio: number
}

export interface CitasPorEstadoDTO {
  estado: string
  cantidad: number
  porcentaje: number
}

export interface ReporteCitasDTO {
  sedeId?: string | null
  sedeNombre: string
  fechaInicio: string
  fechaFin: string
  totalCitas: number
  programadas: number
  confirmadas: number
  atendidas: number
  canceladas: number
  noAsistio: number
  reprogramadas: number
  tasaAsistencia: number
  citasPorDia: CitasPorDiaDTO[]
  citasPorEstado: CitasPorEstadoDTO[]
}

export interface CaptacionDTO {
  fuente: string
  cantidad: number
  porcentaje: number
}

export interface CumpleaneroDTO {
  id: string
  nombreCompleto: string
  dni: string
  telefono?: string | null
  fechaNacimiento: string
  edad: number
  sedeId: string
  sedeNombre: string
}

export interface PacientesMesDTO {
  mes: string
  nuevos: number
  recurrentes: number
}

export interface ReportePacientesDTO {
  sedeId?: string | null
  sedeNombre: string
  fechaInicio: string
  fechaFin: string
  totalPacientes: number
  pacientesNuevos: number
  pacientesRecurrentes: number
  pacientesInactivos: number
  fuentesCaptacion: CaptacionDTO[]
  proximosCumpleanos: CumpleaneroDTO[]
  pacientesPorMes: PacientesMesDTO[]
}

export interface DoctorRendimientoDTO {
  doctorId: string
  nombre: string
  especialidad: string
  citasAtendidas: number
  citasCanceladas: number
  citasNoAsistio: number
  tratamientosRealizados: number
  ingresosGenerados: number
  ticketPromedio: number
  tasaEfectividad: number
}

export interface ReporteDoctoresDTO {
  sedeId?: string | null
  sedeNombre: string
  fechaInicio: string
  fechaFin: string
  doctores: DoctorRendimientoDTO[]
}

export interface ProductoAlertaDTO {
  productoId: string
  codigo: string
  nombre: string
  categoria?: string | null
  sedeId: string
  stockActual: number
  stockMinimo: number
  estado: 'CRITICO' | 'BAJO'
}

export interface LoteAlertaDTO {
  movimientoId: number
  productoNombre: string
  lote: string
  fechaVencimiento: string
  diasParaVencer: number
  sedeId: string
  estado: 'VENCIDO' | 'POR_VENCER'
}

export interface TransferenciaResumenDTO {
  id: string
  productoNombre: string
  cantidad: number
  sedeOrigenId: string
  sedeOrigenNombre: string
  sedeDestinoId: string
  sedeDestinoNombre: string
  estado: string
  fecha: string
}

export interface MovimientoResumenDTO {
  id: number
  productoNombre: string
  tipo: string
  cantidad: number
  motivo?: string | null
  sedeId: string
  fecha: string
}

export interface ReporteInventarioDTO {
  sedeId?: string | null
  sedeNombre: string
  totalProductos: number
  productosStockBajo: number
  productosPorVencer: number
  productosVencidos: number
  transferenciasPendientes: number
  valorizacionTotal: number
  alertasStock: ProductoAlertaDTO[]
  lotesVencimiento: LoteAlertaDTO[]
  transferencias: TransferenciaResumenDTO[]
  movimientosRecientes: MovimientoResumenDTO[]
}

/* ───────── Rendimiento de doctores (Villadent) ───────── */

export interface RendimientoSedeDTO {
  id: string
  nombre: string
}

export interface RendimientoDoctorDTO {
  doctorId: string
  nombre: string
  /** Cobrado en las sedes visibles para quien consulta. */
  cobrado: number
  cobradoPorSede: Record<string, number>
  metaMensual?: number | null
  /** Trabajos de laboratorio del mes: no cuentan para la meta (es por mano de obra). */
  laboratorioDescontado?: number | null
  /** Cobrado menos laboratorio: lo que se compara con la meta. */
  cobradoParaMeta?: number | null
  /** Cobrado total del doctor en Villadent entre su meta, en porcentaje. */
  porcentajeAvance?: number | null
  /** Solo llegan para el administrador general. */
  comisionExcedentePorcentaje?: number | null
  comisionEstimada?: number | null
  citasAtendidas: number
  citasCanceladas: number
  citasNoAsistio: number
  duracionRealPromedioMin?: number | null
  duracionPrevistaPromedioMin?: number | null
  porcentajeCitasQuePasaronDelTiempoPrevisto?: number | null
  esperaPromedioMin?: number | null
}

export interface RendimientoDoctoresDTO {
  mes: string
  sedeId?: string | null
  sedes: RendimientoSedeDTO[]
  totalCobrado: number
  doctores: RendimientoDoctorDTO[]
}

export interface MiAvanceDTO {
  mes: string
  doctorId: string
  nombre: string
  cobrado: number
  metaMensual?: number | null
  /** Trabajos de laboratorio del mes: no cuentan para la meta (es por mano de obra). */
  laboratorioDescontado?: number | null
  /** Cobrado menos laboratorio: lo que se compara con la meta. */
  cobradoParaMeta?: number | null
  porcentajeAvance?: number | null
  citasAtendidas: number
  duracionRealPromedioMin?: number | null
  duracionPrevistaPromedioMin?: number | null
  porcentajeCitasQuePasaronDelTiempoPrevisto?: number | null
  esperaPromedioMin?: number | null
}
