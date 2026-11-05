import { describe, expect, it } from 'vitest'
import { construirDocumentoReporte, escaparHtml } from './reportePacientesDocumento'

const BASE = {
  sede: 'Villadent Chilca',
  emitidoEn: '14/10/2026 18:30',
  fecha: '2026-10-14',
  pacientes: { total: 2, enTratamiento: 1, conTratamientoConcluido: 1, sinTratamiento: 0 },
  resumen: {
    totalTratamientos: 3,
    concluidos: 2,
    enProceso: 1,
    ranking: [
      { posicion: 1, tratamiento: 'Profilaxis', cantidad: 2, concluidos: 2, enProceso: 0 },
      { posicion: 2, tratamiento: 'Ortodoncia', cantidad: 1, concluidos: 0, enProceso: 1 },
    ],
  },
  filas: [
    { dni: '11111111', nombre: 'Ana Quispe', telefono: '999111222', sede: 'Villadent Chilca', tratamiento: 'Ortodoncia', estado: 'En proceso' },
    { dni: '22222222', nombre: 'Luis Rojas', telefono: '', sede: 'Villadent Chilca', tratamiento: 'Sin tratamientos', estado: '' },
  ],
}

describe('Documento del reporte de pacientes', () => {
  it('trae membrete, sede activa, fecha de emisión en hora de Lima y el título para guardar el PDF', () => {
    const html = construirDocumentoReporte(BASE)
    expect(html).toContain('<title>reporte-pacientes-2026-10-14</title>')
    expect(html).toContain('Centro Odontológico Villadent &amp; Centro Tomográfico Villamax 3D')
    expect(html).toContain('Sede activa: Villadent Chilca')
    expect(html).toContain('Fecha de emisión: 14/10/2026 18:30 (hora de Lima)')
    expect(html).toContain('@page { size: A4;')
  })

  it('el ranking sale ordenado con su puesto y la tabla trae las seis columnas pedidas', () => {
    const html = construirDocumentoReporte(BASE)
    expect(html).toContain('<td class="num">1°</td><td>Profilaxis</td><td class="num">2</td>')
    expect(html).toContain('<td class="num">2°</td><td>Ortodoncia</td>')
    for (const columna of ['DNI', 'Nombres y apellidos', 'Celular / WhatsApp', 'Sede', 'Tratamiento', 'Estado']) {
      expect(html).toContain(`>${columna}</th>`)
    }
    // Sin teléfono ni estado se muestra una raya, no un hueco.
    expect(html).toContain('<td>Luis Rojas</td><td>—</td>')
  })

  it('sin tratamientos ni pacientes explica la ausencia en lugar de dejar tablas vacías', () => {
    const html = construirDocumentoReporte({
      ...BASE,
      pacientes: { total: 0, enTratamiento: 0, conTratamientoConcluido: 0, sinTratamiento: 0 },
      resumen: { totalTratamientos: 0, concluidos: 0, enProceso: 0, ranking: [] },
      filas: [],
    })
    expect(html).toContain('Todavía no hay tratamientos en proceso ni concluidos.')
    expect(html).toContain('No hay pacientes con los filtros aplicados.')
    expect(html).not.toContain('<tbody>')
  })

  it('escapa el HTML de los datos: un nombre con etiquetas no se ejecuta ni rompe el documento', () => {
    const html = construirDocumentoReporte({
      ...BASE,
      filas: [{ ...BASE.filas[0], nombre: '<img src=x onerror=alert(1)> & "Cía"', tratamiento: '</td><script>x()</script>' }],
    })
    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt; &amp; &quot;Cía&quot;')
    expect(escaparHtml(undefined)).toBe('')
  })

  it('muestra como máximo los 10 tratamientos más realizados', () => {
    const ranking = Array.from({ length: 14 }, (_, i) => ({ posicion: i + 1, tratamiento: `T${i + 1}`, cantidad: 20 - i, concluidos: 1, enProceso: 0 }))
    const html = construirDocumentoReporte({ ...BASE, resumen: { ...BASE.resumen, ranking } })
    expect(html).toContain('<td>T10</td>')
    expect(html).not.toContain('<td>T11</td>')
  })
})
