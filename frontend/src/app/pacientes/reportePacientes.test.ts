import { describe, expect, it } from 'vitest'
import type { PacienteDTO } from './api-pacientes'
import { construirFilas, construirResumen, contarPacientesPorEstado, fechaHoyLima } from './reportePacientes'

const ANA: PacienteDTO = { id: 'p1', dni: '11111111', nombreCompleto: 'Ana Quispe', telefono: '999111222', sedeId: 'sede-a' }
const LUIS: PacienteDTO = { id: 'p2', dni: '22222222', nombreCompleto: '=Luis, "El" Rojas', telefono: '', sedeId: 'sede-b' }
const SOLA: PacienteDTO = { id: 'p3', dni: '33333333', nombres: 'Sofía', apellidos: 'Sola', sedeId: 'sede-a' }

const TRATAMIENTOS = [
  { pacienteId: 'p1', nombre: 'Ortodoncia', estado: 'EN_PROCESO' },
  { pacienteId: 'p1', nombre: 'Limpieza', estado: 'COMPLETADO' },
  { pacienteId: 'p2', nombre: 'limpieza ', estado: 'COMPLETADO' },
  { pacienteId: 'p2', nombre: 'Implante', estado: 'PLANIFICADO' },
  { pacienteId: 'p2', nombre: 'Corona', estado: 'CANCELADO' },
  { pacienteId: 'p9', nombre: 'Ortodoncia', estado: 'EN_PROCESO' },
]

const sede = (id?: string) => (id === 'sede-a' ? 'Villadent Chilca' : 'Villadent Huancayo Centro')

describe('Reporte de pacientes y tratamientos', () => {
  it('una fila por paciente y tratamiento, solo en proceso o concluidos; sin tratamientos aparece una vez', () => {
    const filas = construirFilas([ANA, LUIS, SOLA], TRATAMIENTOS, sede)
    expect(filas.map((f) => [f.nombre, f.tratamiento, f.estado])).toEqual([
      ['Ana Quispe', 'Ortodoncia', 'En proceso'],
      ['Ana Quispe', 'Limpieza', 'Concluido'],
      ['=Luis, "El" Rojas', 'limpieza', 'Concluido'],
      ['Sofía Sola', 'Sin tratamientos', ''],
    ])
    expect(filas[0]).toMatchObject({ dni: '11111111', telefono: '999111222', sede: 'Villadent Chilca' })
  })

  it('el total y el ranking cuentan toda la clínica, agrupan por nombre sin importar mayúsculas y ordenan de más a menos', () => {
    const resumen = construirResumen(TRATAMIENTOS)
    expect(resumen.totalTratamientos).toBe(4)
    expect(resumen.concluidos).toBe(2)
    expect(resumen.enProceso).toBe(2)
    expect(resumen.ranking).toEqual([
      { posicion: 1, tratamiento: 'Limpieza', cantidad: 2, concluidos: 2, enProceso: 0 },
      { posicion: 2, tratamiento: 'Ortodoncia', cantidad: 2, concluidos: 0, enProceso: 2 },
    ])
  })

  it('sin tratamientos el resumen es cero y el ranking está vacío', () => {
    expect(construirResumen([])).toEqual({ totalTratamientos: 0, concluidos: 0, enProceso: 0, ranking: [] })
  })

  it('cuenta los pacientes en tratamiento, con tratamiento concluido y sin tratamientos', () => {
    const todos = [ANA, LUIS, SOLA]
    // Ana: uno en proceso y uno concluido → cuenta como "en tratamiento". Luis: solo concluido. Sofía: ninguno.
    expect(contarPacientesPorEstado(todos, TRATAMIENTOS)).toEqual({
      total: 3,
      enTratamiento: 1,
      conTratamientoConcluido: 1,
      sinTratamiento: 1,
    })
    expect(contarPacientesPorEstado([], TRATAMIENTOS)).toEqual({ total: 0, enTratamiento: 0, conTratamientoConcluido: 0, sinTratamiento: 0 })
  })

  it('la fecha del nombre del archivo es la de Lima (UTC-5), no la de UTC', () => {
    // 02:00 UTC del día 15 es todavía el día 14 en Lima.
    expect(fechaHoyLima(new Date('2026-10-15T02:00:00Z'))).toBe('2026-10-14')
    expect(fechaHoyLima(new Date('2026-10-15T06:00:00Z'))).toBe('2026-10-15')
  })
})
