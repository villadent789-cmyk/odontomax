import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { cambiarSedeActiva, _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { RotuloMostrando } from './RotuloMostrando'

describe('RotuloMostrando', () => {
  beforeEach(() => {
    sessionStorage.clear()
    _reiniciarCacheSesion()
  })

  it('muestra negocio y "Todas" cuando sedeActivaId es null', () => {
    guardarSesion({
      token: 't',
      usuario: {
        id: 'u1',
        nombre: 'Admin',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 's1', nombre: 'Chilca Villadent', marca: 'VILLADENT' },
          { id: 's2', nombre: 'Huancayo Villadent', marca: 'VILLADENT' },
        ],
      },
      sedeActivaId: 's1',
      negocioActivo: 'VILLADENT',
    })
    cambiarSedeActiva(null)

    render(<RotuloMostrando />)
    const rotulo = screen.getByTestId('rotulo-mostrando')
    expect(rotulo).toHaveTextContent('Mostrando: Villadent · Todas')
  })

  it('muestra negocio y el nombre de la sede activa cuando hay una sede seleccionada', () => {
    guardarSesion({
      token: 't',
      usuario: {
        id: 'u1',
        nombre: 'Admin',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 's1', nombre: 'Chilca Villadent', marca: 'VILLADENT' },
          { id: 'vmx', nombre: 'Chilca Villamax', marca: 'VILLAMAX' },
        ],
      },
      sedeActivaId: 'vmx',
      negocioActivo: 'VILLAMAX',
    })

    render(<RotuloMostrando />)
    const rotulo = screen.getByTestId('rotulo-mostrando')
    expect(rotulo).toHaveTextContent('Mostrando: Villamax 3D · Chilca Villamax')
  })

  it('no renderiza nada si no hay sesion activa', () => {
    limpiarSesion()
    const { container } = render(<RotuloMostrando />)
    expect(container).toBeEmptyDOMElement()
  })
})