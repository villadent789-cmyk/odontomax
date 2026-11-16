import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { InputFechaNacimiento } from './InputFechaNacimiento'
import { formatearFechaEscrita, interpretarFechaEscrita } from './fechaEscrita'

function Prueba({ alCambiar }: { alCambiar: (iso: string, error: string | null) => void }) {
  const [valor, setValor] = useState('')
  return (
    <InputFechaNacimiento
      aria-label="Fecha de nacimiento"
      value={valor}
      onChange={(iso, error) => {
        setValor(iso)
        alCambiar(iso, error)
      }}
    />
  )
}

describe('formatearFechaEscrita', () => {
  it('pone las barras solas mientras se escriben solo números', () => {
    expect(formatearFechaEscrita('1')).toBe('1')
    expect(formatearFechaEscrita('150')).toBe('15/0')
    expect(formatearFechaEscrita('15031985')).toBe('15/03/1985')
    expect(formatearFechaEscrita('15-03-1985')).toBe('15/03/1985')
    expect(formatearFechaEscrita('15.03.1985999')).toBe('15/03/1985')
  })
})

describe('interpretarFechaEscrita', () => {
  const hoy = '2026-11-15'
  it('convierte una fecha válida a ISO', () => {
    expect(interpretarFechaEscrita('15/03/1985', hoy)).toEqual({ iso: '1985-03-15', error: null })
    expect(interpretarFechaEscrita('29/02/2000', hoy)).toEqual({ iso: '2000-02-29', error: null })
  })
  it('incompleta: sin fecha y sin error (todavía está escribiendo)', () => {
    expect(interpretarFechaEscrita('15/03/19', hoy)).toEqual({ iso: '', error: null })
    expect(interpretarFechaEscrita('', hoy)).toEqual({ iso: '', error: null })
  })
  it('rechaza fechas que no existen, muy antiguas o futuras', () => {
    expect(interpretarFechaEscrita('31/02/1980', hoy).error).toMatch(/no existe/)
    expect(interpretarFechaEscrita('29/02/2001', hoy).error).toMatch(/no existe/)
    expect(interpretarFechaEscrita('15/03/1850', hoy).error).toMatch(/no existe/)
    expect(interpretarFechaEscrita('16/11/2026', hoy).error).toMatch(/futura/)
  })
})

describe('InputFechaNacimiento', () => {
  it('se escribe la fecha de corrido y entrega la fecha ISO', async () => {
    const alCambiar = vi.fn()
    render(<Prueba alCambiar={alCambiar} />)
    const campo = screen.getByLabelText('Fecha de nacimiento')

    await userEvent.type(campo, '15031985')

    expect(campo).toHaveValue('15/03/1985')
    expect(alCambiar).toHaveBeenLastCalledWith('1985-03-15', null)
  })

  it('usa el teclado numérico y no es un calendario', () => {
    render(<Prueba alCambiar={() => {}} />)
    const campo = screen.getByLabelText('Fecha de nacimiento')

    expect(campo).toHaveAttribute('type', 'text')
    expect(campo).toHaveAttribute('inputmode', 'numeric')
    expect(campo).toHaveAttribute('placeholder', 'dd/mm/aaaa')
  })

  it('avisa si la fecha completa no existe y no entrega ninguna fecha', async () => {
    const alCambiar = vi.fn()
    render(<Prueba alCambiar={alCambiar} />)

    await userEvent.type(screen.getByLabelText('Fecha de nacimiento'), '31021980')

    expect(await screen.findByRole('alert')).toHaveTextContent(/no existe/)
    expect(alCambiar).toHaveBeenLastCalledWith('', expect.stringMatching(/no existe/))
  })

  it('muestra una fecha que ya viene del formulario', () => {
    render(<InputFechaNacimiento aria-label="Fecha de nacimiento" value="1970-07-04" onChange={() => {}} />)

    expect(screen.getByLabelText('Fecha de nacimiento')).toHaveValue('04/07/1970')
  })
})
