import { Camera, Trash2 } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { cn } from '@/design-system/cn'
import { mensajeError } from '@/shared/errores'
import { useEliminarFotoPaciente, useFotoPaciente, useSubirFotoPaciente } from './api-ficha-ingreso'

interface Props {
  pacienteId: string
  nombres?: string
  apellidos?: string
  /** Cuándo se cambió la foto; nulo si el paciente no tiene. Sirve también para refrescarla al cambiarla. */
  fotoActualizadaEn?: string | null
  tamano?: 'sm' | 'md' | 'lg'
  /** Muestra los botones para subir, cambiar o quitar la foto. */
  editable?: boolean
  className?: string
}

const TAMANOS = { sm: 'size-9 text-xs', md: 'size-12 text-base', lg: 'size-20 text-2xl' }

/** Foto de perfil del paciente; sin foto, sus iniciales. Se recuerda más a una persona por su cara que por su nombre. */
export function FotoPaciente({ pacienteId, nombres, apellidos, fotoActualizadaEn, tamano = 'md', editable = false, className }: Props) {
  const idArchivo = useId()
  const entrada = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const foto = useFotoPaciente(pacienteId, fotoActualizadaEn)
  const subir = useSubirFotoPaciente(pacienteId)
  const quitar = useEliminarFotoPaciente(pacienteId)
  const iniciales = `${(nombres ?? '')[0] ?? ''}${(apellidos ?? '')[0] ?? ''}`.toUpperCase() || '?'
  const nombreCompleto = `${nombres ?? ''} ${apellidos ?? ''}`.trim()

  const elegir = async (archivo?: File) => {
    if (!archivo) return
    setError(null)
    try {
      await subir.mutateAsync(archivo)
    } catch (e) {
      setError(mensajeError(e))
    }
  }

  return (
    <div className={cn('flex flex-col items-center gap-1.5', className)}>
      <div className={cn('relative shrink-0 overflow-hidden rounded-full bg-acento font-bold text-on-acento', TAMANOS[tamano])}>
        {foto.data ? (
          <img src={foto.data} alt={`Foto de ${nombreCompleto}`} className="size-full object-cover" />
        ) : (
          <span aria-hidden className="flex size-full items-center justify-center">
            {iniciales}
          </span>
        )}
      </div>
      {editable && (
        <div className="flex items-center gap-1">
          <input
            ref={entrada}
            id={idArchivo}
            type="file"
            accept="image/*"
            capture="user"
            className="sr-only"
            onChange={(e) => {
              void elegir(e.target.files?.[0])
              e.target.value = ''
            }}
          />
          <button
            type="button"
            onClick={() => entrada.current?.click()}
            disabled={subir.isPending}
            className="inline-flex items-center gap-1 rounded-control px-2 py-1 text-xs font-semibold text-primary hover:bg-surface-container"
          >
            <Camera aria-hidden className="size-3.5" />
            {subir.isPending ? 'Subiendo…' : fotoActualizadaEn ? 'Cambiar foto' : 'Subir foto'}
          </button>
          {fotoActualizadaEn && (
            <button
              type="button"
              aria-label="Quitar foto"
              title="Quitar foto"
              onClick={() => quitar.mutate()}
              disabled={quitar.isPending}
              className="inline-flex size-7 items-center justify-center rounded-control text-on-surface-variant hover:bg-surface-container"
            >
              <Trash2 aria-hidden className="size-3.5" />
            </button>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className="max-w-40 text-center text-[11px] text-error">
          {error}
        </p>
      )}
    </div>
  )
}
