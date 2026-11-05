import { describe, expect, it } from 'vitest'
import { ROLES, puede } from '@/shared/auth'
import { MENU, MODULOS_OCULTOS, menuAgrupadoParaRol, menuParaRol } from './menu'

const etiquetas = (rol: Parameters<typeof menuParaRol>[0]) => menuParaRol(rol).map((i) => i.etiqueta)

describe('menú lateral por rol', () => {
  it('ADMIN_GENERAL ve todo lo operativo, la gestión y el sistema, en ese orden', () => {
    expect(etiquetas('ADMIN_GENERAL')).toEqual([
      'Inicio', 'Agenda', 'Pacientes', 'Nuevo tratamiento', 'Caja',
      'Almacén', 'Laboratorio Dental', 'Reportes',
      'Auditoría', 'Administración',
    ])
  })

  it('ADMIN_SEDE no ve Auditoría ni Administración (el backend se las niega)', () => {
    expect(etiquetas('ADMIN_SEDE')).toEqual([
      'Inicio', 'Agenda', 'Pacientes', 'Nuevo tratamiento', 'Caja', 'Almacén', 'Laboratorio Dental', 'Reportes',
    ])
  })

  it('RECEPCION ve su trabajo diario y la gestión básica, sin reportes ni sistema', () => {
    expect(etiquetas('RECEPCION')).toEqual(['Inicio', 'Agenda', 'Pacientes', 'Caja', 'Almacén', 'Laboratorio Dental'])
  })

  it('ODONTOLOGO ve una lista corta: sin bandeja, caja, reportes ni sistema', () => {
    expect(etiquetas('ODONTOLOGO')).toEqual(['Inicio', 'Agenda', 'Pacientes', 'Nuevo tratamiento', 'Almacén', 'Laboratorio Dental'])
  })

  it('Villamax y Bandeja no aparecen en el menú lateral en esta etapa, aunque las rutas sigan existiendo', () => {
    expect(MODULOS_OCULTOS).toContain('villamax')
    expect(MODULOS_OCULTOS).toContain('bandeja')
    expect(MENU.some((i) => i.etiqueta === 'Villamax Radiología')).toBe(true)
    expect(MENU.some((i) => i.etiqueta === 'Bandeja del día')).toBe(true)
    for (const rol of ROLES) {
      expect(etiquetas(rol)).not.toContain('Villamax Radiología')
      expect(etiquetas(rol)).not.toContain('Bandeja del día')
    }
  })

  it('agrupa en secciones con título y omite las que quedan vacías para el rol', () => {
    expect(menuAgrupadoParaRol('ADMIN_GENERAL').map((s) => [s.titulo, s.items.length])).toEqual([
      ['Día a día', 5],
      ['Gestión', 3],
      ['Sistema', 2],
    ])
    expect(menuAgrupadoParaRol('ODONTOLOGO').map((s) => s.titulo)).toEqual(['Día a día', 'Gestión'])
  })

  it('todo ítem mostrado es uno al que el rol tiene permiso, y las rutas no se repiten', () => {
    for (const rol of ROLES) {
      for (const item of menuParaRol(rol)) expect(puede(rol, item.recurso)).toBe(true)
    }
    const rutas = MENU.map((i) => i.ruta)
    expect(new Set(rutas).size).toBe(rutas.length)
  })

  it('en el negocio Villamax se muestra el menú propio de radiología 3D sin módulos dentales', () => {
    const conVillamax = (rol: Parameters<typeof menuParaRol>[0]) => menuParaRol(rol, 'VILLAMAX').map((i) => i.etiqueta)

    expect(conVillamax('ADMIN_GENERAL')).toEqual([
      'Resumen',
      'Agenda',
      'Pacientes',
      'Nuevo tratamiento',
      'Estudios',
      'Derivaciones y comisiones',
      'Caja',
      'Reportes',
      'Auditoría',
      'Administración',
    ])
    // El doctor ve solo día a día, pero no caja ni reportes ni sistema
    expect(conVillamax('ODONTOLOGO')).toEqual([
      'Resumen',
      'Agenda',
      'Pacientes',
      'Nuevo tratamiento',
      'Estudios',
      'Derivaciones y comisiones',
    ])
    // Recepción ve día a día y caja, pero no comisiones ni reportes ni sistema
    expect(conVillamax('RECEPCION')).toEqual([
      'Resumen',
      'Agenda',
      'Pacientes',
      'Estudios',
      'Caja',
    ])
    // En Villadent se ve el menú dental y no el de Villamax
    expect(menuParaRol('ADMIN_GENERAL', 'VILLADENT').map((i) => i.etiqueta)).not.toContain('Estudios')
    expect(menuParaRol('ADMIN_GENERAL', 'VILLADENT').map((i) => i.etiqueta)).not.toContain('Derivaciones y comisiones')
    expect(conVillamax('ADMIN_GENERAL')).not.toContain('Almacén')
    expect(conVillamax('ADMIN_GENERAL')).not.toContain('Laboratorio Dental')
  })
})
