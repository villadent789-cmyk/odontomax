import { describe, expect, it } from 'vitest'
import { decoracionDeNumero, primitivasDeHallazgos, vecinosDe } from './dienteNorma'
import { buscarHerramienta, colorDeCara } from './herramientasOdontograma'

const h = (id: string) => buscarHerramienta(id)!

describe('dienteNorma', () => {
  it('la restauración temporal se dibuja solo con contorno rojo, sin relleno', () => {
    const c = colorDeCara([{ estado: 'restauracion_temporal', caras: ['O'] }], 'O')
    expect(c.fill).toBe('#FFFFFF')
    expect(c.contorno).toBeTruthy()
  })

  it('la prótesis fija une las piezas vecinas con una sola línea y marca los pilares en los extremos', () => {
    const extremo = primitivasDeHallazgos(true, [h('protesis_fija')], { izq: new Set(), der: new Set(['protesis_fija']) })
    const medio = primitivasDeHallazgos(true, [h('protesis_fija')], { izq: new Set(['protesis_fija']), der: new Set(['protesis_fija']) })
    expect(extremo.filter((p) => p.t === 'linea')).toHaveLength(2)
    expect(medio.filter((p) => p.t === 'linea')).toHaveLength(1)
  })

  it('vecinosDe respeta el orden de la arcada', () => {
    const v = vecinosDe(11, new Map([[12, ['protesis_fija']], [21, ['protesis_fija']]]))
    expect(v.izq.has('protesis_fija')).toBe(true)
    expect(v.der.has('protesis_fija')).toBe(true)
    expect(vecinosDe(18, new Map([[17, ['x']]])).izq.size).toBe(0)
  })

  it.each(['extruido', 'intruido', 'migracion', 'giroversion', 'transposicion', 'diastema', 'supernumerario', 'edentulo_total'])(
    '%s tiene dibujo propio y no solo sigla',
    (id) => {
      expect(primitivasDeHallazgos(false, [h(id)]).length).toBeGreaterThan(0)
    },
  )

  it('clavija y geminación/fusión decoran el número', () => {
    expect(decoracionDeNumero([h('diente_clavija')])?.tipo).toBe('triangulo')
    expect(decoracionDeNumero([h('fusion')])?.tipo).toBe('circulos')
    expect(decoracionDeNumero([h('caries')])).toBeNull()
  })
})
