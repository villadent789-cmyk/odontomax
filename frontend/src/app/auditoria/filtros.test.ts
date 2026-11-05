import { describe, expect, it } from 'vitest'
import type { AuditoriaDTO } from './api-auditoria'
import { SIN_SEDE, detalleLegible, filtrarEventos, normalizar, paginar, tonoModulo, tonoRol } from './filtros'

const ev = (extra: AuditoriaDTO): AuditoriaDTO => ({ id: 'x', fecha: '2026-10-13', ...extra })
const SIN_FILTROS = { texto: '', sede: '', fecha: '' }

describe('auditoría · filtros del cliente', () => {
  const eventos = [
    ev({ id: '1', usuarioNombre: 'Katherin Mendoza', accion: 'CERRAR_CAJA', detalle: 'Curación pieza 16', ip: '1.1.1.1', sedeId: 'a' }),
    ev({ id: '2', usuarioNombre: 'Andrea', accion: 'CREAR_USUARIO', detalle: 'x', ip: '2.2.2.2', fecha: '2026-10-12' }),
  ]

  it('sin filtros devuelve todo', () => {
    expect(filtrarEventos(eventos, SIN_FILTROS)).toHaveLength(2)
  })

  it('busca sin distinguir tildes ni mayúsculas en usuario, detalle, acción e IP', () => {
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, texto: 'CURACION' }).map((e) => e.id)).toEqual(['1'])
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, texto: ' andrea ' }).map((e) => e.id)).toEqual(['2'])
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, texto: 'cerrar_' }).map((e) => e.id)).toEqual(['1'])
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, texto: '2.2.2' }).map((e) => e.id)).toEqual(['2'])
  })

  it('filtra por sede, por "sin sede" y por fecha', () => {
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, sede: 'a' }).map((e) => e.id)).toEqual(['1'])
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, sede: SIN_SEDE }).map((e) => e.id)).toEqual(['2'])
    expect(filtrarEventos(eventos, { ...SIN_FILTROS, fecha: '2026-10-12' }).map((e) => e.id)).toEqual(['2'])
  })

  it('tolera campos ausentes', () => {
    expect(filtrarEventos([{ id: '3' }], { ...SIN_FILTROS, texto: 'algo' })).toEqual([])
    expect(normalizar(undefined)).toBe('')
  })
})

describe('auditoría · paginación y presentación', () => {
  it('pagina y acota la página pedida al rango válido', () => {
    const datos = Array.from({ length: 53 }, (_, i) => i)
    expect(paginar(datos, 1).items).toHaveLength(25)
    expect(paginar(datos, 3)).toMatchObject({ pagina: 3, totalPaginas: 3 })
    expect(paginar(datos, 3).items).toEqual([50, 51, 52])
    expect(paginar(datos, 99).pagina).toBe(3)
    expect(paginar(datos, 0).pagina).toBe(1)
    expect(paginar([], 1)).toEqual({ items: [], pagina: 1, totalPaginas: 1 })
  })

  it('identa el JSON y deja intacto el texto plano o el JSON roto', () => {
    expect(detalleLegible('{"a":1}')).toEqual({ json: true, texto: '{\n  "a": 1\n}' })
    expect(detalleLegible('Caja cerrada')).toEqual({ json: false, texto: 'Caja cerrada' })
    expect(detalleLegible('{roto')).toEqual({ json: false, texto: '{roto' })
    expect(detalleLegible(undefined)).toEqual({ json: false, texto: '' })
  })

  it('asigna tonos por módulo y rol, con neutro por defecto', () => {
    expect(tonoModulo('CAJA')).toBe('accion')
    expect(tonoModulo('USUARIOS')).toBe('peligro')
    expect(tonoModulo('OTRO')).toBe('neutro')
    expect(tonoRol('RECEPCION')).toBe('accion')
    expect(tonoRol('SISTEMA')).toBe('neutro')
  })
})
