export interface DoctorDerivadorDTO {
  id: string
  nombre: string
  cop?: string
  clinicaOrigen?: string
  telefono?: string
  banco?: string
  numeroCuentaOYape?: string
  porcentajeOTarifaFija?: number
  activo: boolean
  notas?: string
  totalComisionesPendientes?: number
  cantidadEstudiosDerivados?: number
  creadoEn?: string
}

export interface DoctorDerivadorRequestDTO {
  nombre: string
  cop?: string
  clinicaOrigen?: string
  telefono?: string
  banco?: string
  numeroCuentaOYape?: string
  porcentajeOTarifaFija?: number
  activo?: boolean
  notas?: string
}

export interface ComisionRadiologicaDTO {
  id: string
  doctorDerivadorId: string
  doctorDerivadorNombre: string
  doctorDerivadorCop?: string
  doctorDerivadorClinica?: string
  doctorDerivadorTelefono?: string
  doctorDerivadorCuenta?: string
  pacienteNombre: string
  pacienteId?: string
  tipoEstudio: string
  precioCobrado: number
  montoComision: number
  /** Cuántos estudios cubre el registro (10 radiografías = 10 × la comisión fija). */
  cantidad?: number
  /** Paciente estudiante con convenio: sin comisión. */
  esEstudiante?: boolean
  estado: 'PENDIENTE' | 'LIQUIDADO' | 'ANULADO'
  fechaEmision: string
  fechaLiquidacion?: string
  metodoPago?: string
  codigoOperacion?: string
  nota?: string
  sedeId: string
  registradoPor?: string
  creadoEn: string
}

export interface ComisionRadiologicaCreateRequestDTO {
  doctorDerivadorId: string
  pacienteNombre: string
  pacienteId?: string
  /** Estudio del catálogo de Villamax: de él salen el nombre, el precio y la comisión. */
  estudioId: string
  /** Número de estudios; la comisión fija se multiplica por esta cantidad. */
  cantidad?: number
  /** Paciente estudiante: precio manual y sin comisión. */
  esEstudiante?: boolean
  /** Solo con paciente estudiante: precio convenido (para todos los estudios). */
  precioCobrado?: number
  nota?: string
  sedeId?: string
}

export interface ComisionRadiologicaLiquidarRequestDTO {
  metodoPago: string
  codigoOperacion?: string
  nota?: string
}

export interface ResumenComisionesDTO {
  totalMontoPendiente: number
  cantidadPendientes: number
  totalMontoLiquidado: number
  cantidadLiquidados: number
  totalDoctoresDerivadoresActivos: number
}
