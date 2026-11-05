import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EncabezadoImpresion } from './EncabezadoImpresion'
import type { Sesion } from '@/shared/auth'

const SESION_VILLADENT: Sesion = {
  token: 'tk',
  negocioActivo: 'VILLADENT',
  sedeActivaId: 'sede-chilca-1',
  usuario: {
    id: '1',
    nombre: 'Admin General',
    rol: 'ADMIN_GENERAL',
    sedes: [
      { id: 'sede-chilca-1', nombre: 'Chilca', marca: 'VILLADENT' },
      { id: 'sede-eltambo', nombre: 'Huancayo Centro', marca: 'VILLADENT' },
    ],
  },
}

const SESION_VILLAMAX: Sesion = {
  token: 'tk',
  negocioActivo: 'VILLAMAX',
  sedeActivaId: 'sede-villamax-chilca',
  usuario: {
    id: '2',
    nombre: 'Doctor Villamax',
    rol: 'ODONTOLOGO',
    sedes: [{ id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' }],
  },
}

describe('EncabezadoImpresion', () => {
  it('renderiza el encabezado limpio de Villadent con sede, negocio y fecha de impresión', () => {
    render(<EncabezadoImpresion sesion={SESION_VILLADENT} />)

    const header = screen.getByTestId('encabezado-impresion')
    expect(header).toBeInTheDocument()
    expect(header.className).toContain('hidden')
    expect(header.className).toContain('print:flex')

    expect(screen.getByText('VILLADENT')).toBeInTheDocument()
    expect(screen.getByText('Chilca')).toBeInTheDocument()
    expect(screen.getByText('Red Odontológica · Junín')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Villadent' })).toHaveAttribute('src', '/logo-villadent-128.png')
  })

  it('renderiza el encabezado de Villamax con insignia y sede correspondiente', () => {
    render(<EncabezadoImpresion sesion={SESION_VILLAMAX} />)

    const header = screen.getByTestId('encabezado-impresion')
    expect(header).toBeInTheDocument()
    expect(screen.getByText('VILLAMAX 3D')).toBeInTheDocument()
    expect(screen.getByText('Villamax Chilca')).toBeInTheDocument()
    expect(screen.getByText('Centro Radiológico Maxilofacial')).toBeInTheDocument()
  })

  it('muestra "Todas las sedes" cuando no hay sede activa seleccionada', () => {
    const sesionTodas = { ...SESION_VILLADENT, sedeActivaId: null }
    render(<EncabezadoImpresion sesion={sesionTodas} />)

    expect(screen.getByText('Todas las sedes')).toBeInTheDocument()
  })
})
