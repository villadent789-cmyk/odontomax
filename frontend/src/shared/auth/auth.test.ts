import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { esRutaPermitidaParaNegocio, evaluarAcceso } from './guards'
import { PERMISOS, ROLES, esRol, puede, type Rol } from './roles'
import {
  _reiniciarCacheSesion,
  cambiarNegocioActivo,
  cambiarSedeActiva,
  guardarSesion,
  limpiarSesion,
  negociosDe,
  obtenerNegocioActivo,
  obtenerSesion,
  obtenerToken,
  type SedeSesion,
  type Sesion,
} from './sesion'

function sesion(rol: Rol, sedes: SedeSesion[] = [{ id: 'sede-a', nombre: 'Sede A', marca: 'VILLADENT' }]): Sesion {
  return { token: 't', usuario: { nombre: 'Prueba', rol, sedes }, sedeActivaId: null }
}

describe('tabla de permisos (la más prudente; el backend decide la seguridad real)', () => {
  it('todos los roles ven inicio, agenda y pacientes', () => {
    for (const rol of ROLES) {
      expect(puede(rol, 'inicio')).toBe(true)
      expect(puede(rol, 'agenda')).toBe(true)
      expect(puede(rol, 'pacientes')).toBe(true)
    }
  })

  it('RECEPCION no ve configuración ni estadísticas, pero sí caja', () => {
    expect(puede('RECEPCION', 'configuracion')).toBe(false)
    expect(puede('RECEPCION', 'estadisticas')).toBe(false)
    expect(puede('RECEPCION', 'caja')).toBe(true)
  })

  it('ODONTOLOGO no ve caja, configuración ni estadísticas', () => {
    expect(puede('ODONTOLOGO', 'caja')).toBe(false)
    expect(puede('ODONTOLOGO', 'configuracion')).toBe(false)
    expect(puede('ODONTOLOGO', 'estadisticas')).toBe(false)
    expect(puede('ODONTOLOGO', 'registrarPaciente')).toBe(false)
  })

  it('ADMIN_GENERAL ve todo', () => {
    for (const recurso of Object.keys(PERMISOS) as (keyof typeof PERMISOS)[]) {
      expect(puede('ADMIN_GENERAL', recurso)).toBe(true)
    }
  })

  it('sin rol no se puede nada y solo se aceptan roles conocidos', () => {
    expect(puede(null, 'inicio')).toBe(false)
    expect(esRol('RECEPCION')).toBe(true)
    expect(esRol('SUPERADMIN')).toBe(false)
  })
})

describe('evaluarAcceso (guardas de ruta)', () => {
  it('sin sesión manda al login', () => {
    expect(evaluarAcceso(null)).toBe('login')
    expect(evaluarAcceso(null, 'agenda')).toBe('login')
  })

  it('con sesión y sin permiso manda a "Sin permiso"', () => {
    expect(evaluarAcceso(sesion('RECEPCION'), 'configuracion')).toBe('sin-permiso')
    expect(evaluarAcceso(sesion('ODONTOLOGO'), 'caja')).toBe('sin-permiso')
  })

  it('con permiso deja pasar', () => {
    expect(evaluarAcceso(sesion('ADMIN_GENERAL'), 'configuracion')).toBe('permitido')
    expect(evaluarAcceso(sesion('ADMIN_SEDE'), 'estadisticas')).toBe('permitido')
    expect(evaluarAcceso(sesion('RECEPCION'))).toBe('permitido')
  })

  it('Administración es solo del administrador general: ADMIN_SEDE ya no ve un acceso que el backend le niega', () => {
    expect(evaluarAcceso(sesion('ADMIN_SEDE'), 'configuracion')).toBe('sin-permiso')
    expect(puede('ADMIN_SEDE', 'configuracion')).toBe(false)
    expect(puede('ADMIN_GENERAL', 'configuracion')).toBe(true)
  })
})

describe('esRutaPermitidaParaNegocio y guardas de aislamiento por negocio', () => {
  it('rutas exclusivas de Villamax solo se permiten en VILLAMAX', () => {
    expect(esRutaPermitidaParaNegocio('/app/radiologia', 'VILLAMAX')).toBe(true)
    expect(esRutaPermitidaParaNegocio('/app/radiologia/comisiones', 'VILLAMAX')).toBe(true)
    expect(esRutaPermitidaParaNegocio('/app/villamax', 'VILLAMAX')).toBe(true)
    expect(esRutaPermitidaParaNegocio('/app/villamax/estudios', 'VILLAMAX')).toBe(true)

    expect(esRutaPermitidaParaNegocio('/app/radiologia', 'VILLADENT')).toBe(false)
    expect(esRutaPermitidaParaNegocio('/app/villamax/estudios', 'VILLADENT')).toBe(false)
    expect(esRutaPermitidaParaNegocio('/app/villamax', 'VILLADENT')).toBe(false)
  })

  it('rutas exclusivas de Villadent solo se permiten en VILLADENT', () => {
    expect(esRutaPermitidaParaNegocio('/app/inventario', 'VILLADENT')).toBe(true)
    expect(esRutaPermitidaParaNegocio('/app/laboratorio', 'VILLADENT')).toBe(true)

    expect(esRutaPermitidaParaNegocio('/app/inventario', 'VILLAMAX')).toBe(false)
    expect(esRutaPermitidaParaNegocio('/app/laboratorio', 'VILLAMAX')).toBe(false)
  })

  it('rutas comunes se permiten en ambos negocios', () => {
    for (const negocio of ['VILLADENT', 'VILLAMAX'] as const) {
      expect(esRutaPermitidaParaNegocio('/app', negocio)).toBe(true)
      expect(esRutaPermitidaParaNegocio('/app/agenda', negocio)).toBe(true)
      expect(esRutaPermitidaParaNegocio('/app/pacientes', negocio)).toBe(true)
      expect(esRutaPermitidaParaNegocio('/app/caja', negocio)).toBe(true)
    }
  })

  it('evaluarAcceso redirige silenciosamente al inicio si la pantalla no pertenece al negocio', () => {
    const sVilladent: Sesion = { ...sesion('ADMIN_GENERAL'), negocioActivo: 'VILLADENT' }
    const sVillamax: Sesion = { ...sesion('ADMIN_GENERAL'), negocioActivo: 'VILLAMAX' }

    expect(evaluarAcceso(sVilladent, 'villamax', '/app/radiologia')).toBe('redireccion-negocio')
    expect(evaluarAcceso(sVilladent, undefined, '/app/villamax/estudios')).toBe('redireccion-negocio')

    expect(evaluarAcceso(sVillamax, 'inventario', '/app/inventario')).toBe('redireccion-negocio')
    expect(evaluarAcceso(sVillamax, 'laboratorio', '/app/laboratorio')).toBe('redireccion-negocio')
  })
})

describe('almacenamiento de sesión', () => {
  beforeEach(() => {
    sessionStorage.clear()
    _reiniciarCacheSesion()
  })
  afterEach(() => limpiarSesion())

  it('guarda en sessionStorage, elige la primera sede y la recupera tras recargar', () => {
    guardarSesion(sesion('RECEPCION'))
    _reiniciarCacheSesion()
    expect(obtenerSesion()?.usuario.rol).toBe('RECEPCION')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-a')
    expect(obtenerToken()).toBe('t')
  })

  it('descarta datos corruptos o con rol desconocido', () => {
    sessionStorage.setItem('odontomax.sesion', '{"token":"x","usuario":{"nombre":"a","rol":"ROOT","sedes":[]}}')
    expect(obtenerSesion()).toBeNull()
    _reiniciarCacheSesion()
    sessionStorage.setItem('odontomax.sesion', 'no-json')
    expect(obtenerSesion()).toBeNull()
  })

  it('solo cambia a sedes del usuario', () => {
    guardarSesion(
      sesion('ADMIN_GENERAL', [
        { id: 'sede-a', nombre: 'A', marca: 'VILLADENT' },
        { id: 'sede-c', nombre: 'C', marca: 'VILLAMAX' },
      ]),
    )
    cambiarSedeActiva('sede-c')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-c')
    cambiarSedeActiva('sede-x')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-c')
    cambiarSedeActiva(null)
    expect(obtenerSesion()?.sedeActivaId).toBeNull()
  })

  it('con sedes de los dos negocios el activo empieza en el de su sede y cambiarlo deja "todas" o su única sede', () => {
    guardarSesion(
      sesion('ADMIN_GENERAL', [
        { id: 'sede-a', nombre: 'A', marca: 'VILLADENT' },
        { id: 'sede-b', nombre: 'B', marca: 'VILLADENT' },
        { id: 'sede-c', nombre: 'C', marca: 'VILLAMAX' },
      ]),
    )
    expect(negociosDe(obtenerSesion()!.usuario.sedes)).toEqual(['VILLADENT', 'VILLAMAX'])
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')
    expect(obtenerNegocioActivo()).toBe('VILLADENT')

    cambiarNegocioActivo('VILLAMAX')
    expect(obtenerSesion()?.negocioActivo).toBe('VILLAMAX')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-c')
    expect(obtenerNegocioActivo()).toBe('VILLAMAX')

    cambiarNegocioActivo('VILLADENT')
    expect(obtenerSesion()?.sedeActivaId).toBeNull()
  })

  it('con un solo negocio se envía ese negocio (el catálogo lo necesita) y no se puede cambiar a otro', () => {
    guardarSesion(sesion('RECEPCION', [{ id: 'sede-a', nombre: 'A', marca: 'VILLADENT' }]))

    expect(obtenerNegocioActivo()).toBe('VILLADENT')
    cambiarNegocioActivo('VILLAMAX')
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-a')
  })

  it('garantiza invariante: nunca queda una sede de un negocio con el otro negocio activo', () => {
    // Si sessionStorage contiene datos inconsistentes (negocio VILLAMAX pero sede de VILLADENT)
    sessionStorage.setItem(
      'odontomax.sesion',
      JSON.stringify({
        token: 't',
        usuario: {
          nombre: 'Admin',
          rol: 'ADMIN_GENERAL',
          sedes: [
            { id: 'sede-v1', nombre: 'Chilca Villadent', marca: 'VILLADENT' },
            { id: 'sede-vmx', nombre: 'Chilca Villamax', marca: 'VILLAMAX' },
          ],
        },
        negocioActivo: 'VILLAMAX',
        sedeActivaId: 'sede-v1', // Inconsistente!
      }),
    )
    _reiniciarCacheSesion()
    const s = obtenerSesion()
    expect(s?.negocioActivo).toBe('VILLAMAX')
    expect(s?.sedeActivaId).toBe('sede-vmx') // Auto-corregido a la sede del negocio activo!
  })

  it('cambiar sede activa sincroniza el negocio activo y garantiza el invariante', () => {
    guardarSesion(
      sesion('ADMIN_GENERAL', [
        { id: 'sede-v1', nombre: 'Chilca Villadent', marca: 'VILLADENT' },
        { id: 'sede-v2', nombre: 'Huancayo Villadent', marca: 'VILLADENT' },
        { id: 'sede-vmx', nombre: 'Chilca Villamax', marca: 'VILLAMAX' },
      ]),
    )
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')
    cambiarSedeActiva('sede-v1')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-v1')
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')

    // Al cambiar a sede-vmx (Villamax), el negocio se sincroniza inmediatamente a VILLAMAX
    cambiarSedeActiva('sede-vmx')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-vmx')
    expect(obtenerSesion()?.negocioActivo).toBe('VILLAMAX')

    // Y al cambiar negocio a VILLADENT, la sede de VILLAMAX ya no queda activa (vuelve a null/Todas)
    cambiarNegocioActivo('VILLADENT')
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')
    expect(obtenerSesion()?.sedeActivaId).toBeNull()
  })

  it('limpiarSesion borra todo', () => {
    guardarSesion(sesion('RECEPCION'))
    limpiarSesion()
    expect(obtenerSesion()).toBeNull()
    expect(sessionStorage.getItem('odontomax.sesion')).toBeNull()
  })
})
