import { describe, expect, it } from 'vitest'
import { documentoFicha } from './documentoFicha'
import type { PlantillaTratamiento } from './api-plantillas'

const PLANTILLA: PlantillaTratamiento = {
  codigo: 'RESTAURACION',
  nombre: 'Restauración',
  negocio: 'VILLADENT',
  version: 1,
  esquema: {
    secciones: [
      {
        titulo: 'Pieza',
        campos: [
          { id: 'pieza', etiqueta: 'Pieza dental (FDI)', tipo: 'pieza' },
          { id: 'caras', etiqueta: 'Caras', tipo: 'multiple' },
          { id: 'aislamiento', etiqueta: 'Aislamiento absoluto', tipo: 'si_no' },
          { id: 'material', etiqueta: 'Material', tipo: 'texto' },
        ],
      },
    ],
  },
}

describe('documentoFicha', () => {
  it('imprime la cabecera del negocio, el paciente y cada campo con valores legibles', () => {
    const html = documentoFicha({
      plantilla: PLANTILLA,
      datos: { pieza: 24, caras: ['Oclusal', 'Mesial'], aislamiento: true },
      pacienteNombre: 'Carlos Rojas',
      estado: 'CERRADO',
      doctor: 'Doctor B',
    })
    expect(html).toContain('VILLADENT')
    expect(html).toContain('Ficha de tratamiento · cerrada')
    expect(html).toContain('Carlos Rojas')
    expect(html).toContain('24')
    expect(html).toContain('Oclusal, Mesial')
    expect(html).toContain('<td>Sí</td>')
    // Lo que no se llenó se ve como guion, no vacío.
    expect(html).toContain('<th>Material</th><td>—</td>')
  })

  it('escapa lo que escribió el doctor para que no se interprete como HTML', () => {
    const html = documentoFicha({ plantilla: PLANTILLA, datos: { material: '<script>alert(1)</script>' }, pacienteNombre: 'A & B' })
    expect(html).not.toContain('<script>alert(1)</script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('A &amp; B')
  })
})
