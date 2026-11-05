import { describe, expect, it } from 'vitest'
import { documentoOdontograma } from './documentoOdontograma'
import { lineasEspecificaciones } from './especificaciones'

describe('Especificaciones del odontograma', () => {
  it('explica cada hallazgo en una línea por pieza, en orden FDI, con caras, sigla y observación', () => {
    const lineas = lineasEspecificaciones([
      { piezaFdi: 26, estado: 'corona_cmc', caras: [], observacion: 'dorada' },
      { piezaFdi: 16, estado: 'caries', caras: ['O', 'D'] },
      { piezaFdi: 16, estado: 'endodoncia', caras: [] },
    ])
    expect(lineas.map((l) => l.piezaFdi)).toEqual([16, 26])
    expect(lineas[0].texto).toBe('Caries dental, cara oclusal y distal; Tratamiento de conductos (TC)')
    expect(lineas[1].texto).toBe('Corona metal cerámica (CMC) — dorada')
  })

  it('la hoja impresa lleva el recuadro de Especificaciones y el espacio para la firma', () => {
    const html = documentoOdontograma({
      pacienteNombre: 'Rosa Quispe',
      sedeNombre: 'Villadent Chilca',
      capturas: [{ titulo: 'Inicial', fecha: '2026-10-30T10:00:00Z', piezas: [{ piezaFdi: 16, estado: 'caries', caras: ['O'] }] }],
    })
    expect(html).toContain('Especificaciones')
    expect(html).toContain('<b>16:</b> Caries dental, cara oclusal.')
    expect(html).toContain('Firma y sello del profesional responsable')
  })
})

describe('hallazgo radiográfico', () => {
  it('se marca y se separa de la observación', async () => {
    const { unirRadiografia, separarRadiografia } = await import('./especificaciones')
    expect(unirRadiografia(true, '')).toBe('Según radiografía')
    expect(unirRadiografia(true, 'periapical')).toBe('Según radiografía: periapical')
    expect(unirRadiografia(false, ' nota ')).toBe('nota')
    expect(separarRadiografia('Según radiografía: periapical')).toEqual({ radiografico: true, texto: 'periapical' })
    expect(separarRadiografia('otra')).toEqual({ radiografico: false, texto: 'otra' })
  })
})
