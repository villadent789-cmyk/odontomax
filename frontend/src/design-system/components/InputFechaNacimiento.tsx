import { useState, type ComponentProps } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { Input } from './Input'
import { formatearFechaEscrita, interpretarFechaEscrita, isoATexto } from './fechaEscrita'

type Props = Omit<ComponentProps<typeof Input>, 'value' | 'onChange' | 'type' | 'inputMode'> & {
  /** Fecha en formato ISO (aaaa-mm-dd) o vacío. */
  value: string
  /** Recibe la fecha ISO (vacía si aún está incompleta o no es válida) y el motivo si lo escrito no vale. */
  onChange: (iso: string, error: string | null) => void
}

/**
 * Fecha de nacimiento que se ESCRIBE (o se dicta) con el teclado numérico: el sistema pone las barras solo. Evita el
 * calendario, lento para fechas de hace décadas. Entrega la fecha en ISO al formulario.
 */
export function InputFechaNacimiento({ value, onChange, placeholder = 'dd/mm/aaaa', onBlur, ...resto }: Props) {
  const [texto, setTexto] = useState(() => isoATexto(value))
  const [mostrarError, setMostrarError] = useState(false)
  const { error } = interpretarFechaEscrita(texto)

  // Si el formulario cambia la fecha por fuera (p. ej. la limpia tras guardar), el texto se actualiza.
  useAlCambiar([value], () => {
    if (interpretarFechaEscrita(texto).iso !== value) setTexto(isoATexto(value))
  })

  function cambiar(crudo: string) {
    const nuevo = formatearFechaEscrita(crudo)
    setTexto(nuevo)
    const r = interpretarFechaEscrita(nuevo)
    setMostrarError(r.error !== null)
    onChange(r.iso, r.error)
  }

  return (
    <>
      <Input
        {...resto}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder={placeholder}
        maxLength={10}
        value={texto}
        onChange={(e) => cambiar(e.target.value)}
        onBlur={(e) => {
          setMostrarError(error !== null)
          onBlur?.(e)
        }}
        aria-invalid={error && mostrarError ? true : resto['aria-invalid']}
      />
      {error && mostrarError && (
        <p role="alert" className="text-sm font-bold text-error">
          {error}
        </p>
      )}
    </>
  )
}
