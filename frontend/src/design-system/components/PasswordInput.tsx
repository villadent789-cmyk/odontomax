import { Eye, EyeOff, KeyRound } from 'lucide-react'
import { useState } from 'react'
import { Input, type InputProps } from './Input'

export type PasswordInputProps = Omit<InputProps, 'type' | 'sufijo'>

/** Campo de contraseña con botón para mostrar/ocultar (accesible por teclado, con aria-pressed). */
export function PasswordInput({ icono = <KeyRound />, autoComplete = 'current-password', ...props }: PasswordInputProps) {
  const [visible, setVisible] = useState(false)
  return (
    <Input
      {...props}
      icono={icono}
      autoComplete={autoComplete}
      type={visible ? 'text' : 'password'}
      sufijo={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-pressed={visible}
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          disabled={props.disabled}
          className="inline-flex size-9 items-center justify-center rounded-pill text-on-surface-variant hover:bg-surface-container-high hover:text-primary focus-visible:outline-2 focus-visible:outline-focus disabled:opacity-50 [&_svg]:size-5"
        >
          {visible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
        </button>
      }
    />
  )
}
