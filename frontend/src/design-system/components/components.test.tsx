import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Button } from './Button'
import { ESTADOS, humanizarEstado } from '../estados'
import { EstadoBadge } from './EstadoBadge'
import { Field } from './Field'
import { Input } from './Input'
import { PasswordInput } from './PasswordInput'
import { iniciales } from '../iniciales'

describe('Field', () => {
  it('enlaza etiqueta, error y ayuda con el control', () => {
    render(
      <Field label="DNI" ayuda="8 dígitos" error="DNI inválido">
        <Input />
      </Field>,
    )
    const input = screen.getByLabelText('DNI')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('DNI inválido 8 dígitos')
  })
})

describe('PasswordInput', () => {
  it('muestra y oculta la contraseña con un botón accesible', async () => {
    render(
      <Field label="Contraseña">
        <PasswordInput />
      </Field>,
    )
    const input = screen.getByLabelText('Contraseña')
    expect(input).toHaveAttribute('type', 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))
    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('Button', () => {
  it('se deshabilita y marca aria-busy mientras carga', () => {
    render(<Button cargando>Guardar</Button>)
    const boton = screen.getByRole('button', { name: 'Guardar' })
    expect(boton).toBeDisabled()
    expect(boton).toHaveAttribute('aria-busy', 'true')
  })
})

describe('EstadoBadge', () => {
  it('el mapa único incluye los estados oficiales del Backend v2 (sección 4)', () => {
    expect(ESTADOS).toHaveProperty('PENDIENTE_DE_PAGO')
    expect(ESTADOS).toHaveProperty('CONFIRMADA_CON_ADELANTO')
    expect(ESTADOS).toHaveProperty('EXPIRADA')
  })

  it('muestra el texto en español del mapa único', () => {
    render(<EstadoBadge estado="PENDIENTE_WEB" />)
    expect(screen.getByText('Pendiente de confirmación')).toHaveAttribute('data-estado', 'PENDIENTE_WEB')
  })

  it('humaniza estados no mapeados', () => {
    expect(humanizarEstado('PENDIENTE_DE_PAGO')).toBe('Pendiente de pago')
    render(<EstadoBadge estado="EN_ESPERA" />)
    expect(screen.getByText('En espera')).toBeInTheDocument()
  })

  it('usa el texto del mapa cuando existe', () => {
    render(<EstadoBadge estado="X" mapa={{ X: { texto: 'Confirmada', tono: 'primario' } }} />)
    expect(screen.getByText('Confirmada')).toBeInTheDocument()
  })
})

describe('iniciales', () => {
  it('toma la primera y la última palabra', () => {
    expect(iniciales('Katherin Mendoza')).toBe('KM')
    expect(iniciales('lucía')).toBe('L')
    expect(iniciales('Katherin Mendoza (demo)')).toBe('KM')
    expect(iniciales('  ')).toBe('?')
  })
})
