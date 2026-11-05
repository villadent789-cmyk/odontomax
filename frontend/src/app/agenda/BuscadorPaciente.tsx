import { zodResolver } from '@hookform/resolvers/zod'
import { Search, UserPlus, UserRound } from 'lucide-react'
import { useDeferredValue, useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Alert, Button, Field, Input, Skeleton } from '@/design-system'
import { cn } from '@/design-system/cn'
import { hoyEnLima } from '@/shared/format'
import { nombrePaciente, useCrearPaciente, usePacientes, type Paciente } from './api-agenda'
import { mensajeError } from './errores'
import { esquemaPaciente, type DatosPaciente } from './esquemas'

interface Props {
  sedeId: string | null
  seleccionado: Paciente | null
  onSeleccionar: (p: Paciente | null) => void
  error?: string
}

/**
 * Paciente de la cita: búsqueda en GET /pacientes?busqueda (3+ caracteres, sede activa) o registro rápido con
 * POST /pacientes (cuerpo de PacienteRequestDTO: dni, nombres, apellidos, telefono, fechaNacimiento, sedeId).
 */
export function BuscadorPaciente({ sedeId, seleccionado, onSeleccionar, error }: Props) {
  const [texto, setTexto] = useState('')
  const [registrando, setRegistrando] = useState(false)
  const busqueda = useDeferredValue(texto)
  const pacientes = usePacientes(sedeId, busqueda)
  const idError = useId()

  if (seleccionado) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-panel bg-primary-fixed/50 p-3">
        <p className="flex items-center gap-2">
          <UserRound aria-hidden className="size-5 text-primary" />
          <span>
            <strong>{nombrePaciente(seleccionado)}</strong>
            <span className="text-sm text-on-surface-variant"> · DNI {seleccionado.dni ?? '—'}</span>
          </span>
        </p>
        <Button size="sm" variant="secundario" onClick={() => onSeleccionar(null)}>
          Cambiar paciente
        </Button>
      </div>
    )
  }

  if (registrando) {
    return (
      <RegistroRapido
        sedeId={sedeId}
        dniInicial={/^\d{8}$/.test(texto.trim()) ? texto.trim() : ''}
        onCancelar={() => setRegistrando(false)}
        onRegistrado={(p) => {
          setRegistrando(false)
          onSeleccionar(p)
        }}
      />
    )
  }

  const resultados = (pacientes.data ?? []).filter((p) => !p.archivado).slice(0, 8)
  return (
    <div className="flex flex-col gap-3">
      <Field label="Paciente" ayuda="Escribe al menos 3 caracteres del DNI, nombre o apellido." error={error}>
        <Input
          icono={<Search />}
          type="search"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="DNI o nombre del paciente"
          autoComplete="off"
        />
      </Field>
      {busqueda.trim().length >= 3 && (
        <div aria-live="polite">
          {pacientes.isPending ? (
            <div aria-busy="true" className="flex flex-col gap-2">
              <span className="sr-only">Buscando pacientes…</span>
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : pacientes.isError ? (
            <Alert tono="error" titulo="No se pudo buscar">
              {mensajeError(pacientes.error)}
            </Alert>
          ) : resultados.length === 0 ? (
            <p className="text-sm text-on-surface-variant">No hay pacientes que coincidan con «{busqueda.trim()}».</p>
          ) : (
            <ul aria-label="Pacientes encontrados" aria-describedby={error ? idError : undefined} className="flex flex-col gap-2">
              {resultados.map((p) => (
                <li key={p.id}>
                  <button
                    type="button"
                    onClick={() => onSeleccionar(p)}
                    className="flex w-full flex-wrap items-center justify-between gap-2 rounded-panel bg-surface-container-low px-4 py-3 text-left hover:bg-surface-container-high focus-visible:outline-2 focus-visible:outline-focus"
                  >
                    <span className="font-bold">{nombrePaciente(p)}</span>
                    <span className="text-sm text-on-surface-variant">
                      DNI {p.dni ?? '—'}
                      {p.telefono ? ` · ${p.telefono}` : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <Button variant="secundario" size="sm" className="self-start" onClick={() => setRegistrando(true)}>
        <UserPlus aria-hidden />
        Registrar paciente nuevo
      </Button>
    </div>
  )
}

function RegistroRapido({
  sedeId,
  dniInicial,
  onCancelar,
  onRegistrado,
}: {
  sedeId: string | null
  dniInicial: string
  onCancelar: () => void
  onRegistrado: (p: Paciente) => void
}) {
  const crear = useCrearPaciente()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<DatosPaciente>({
    resolver: zodResolver(esquemaPaciente),
    defaultValues: { dni: dniInicial, nombres: '', apellidos: '', telefono: '', fechaNacimiento: '' },
  })

  // Va dentro del formulario de la cita: no puede ser un <form> anidado, se envía con el botón.
  const registrar = handleSubmit(async (d) => {
    try {
      const p = await crear.mutateAsync({
        dni: d.dni,
        nombres: d.nombres,
        apellidos: d.apellidos,
        telefono: d.telefono || undefined,
        fechaNacimiento: d.fechaNacimiento || undefined,
        sedeId: sedeId ?? undefined,
      })
      onRegistrado(p)
    } catch {
      /* se muestra el error */
    }
  })

  return (
    <div role="group" aria-labelledby="titulo-registro" className={cn('flex flex-col gap-3 rounded-panel bg-surface-container-low p-4')}>
      <p id="titulo-registro" className="font-bold text-primary">
        Registrar paciente nuevo
      </p>
      {crear.isError && (
        <Alert tono="error" titulo="No se pudo registrar al paciente">
          {mensajeError(crear.error)}
        </Alert>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label="DNI" error={errors.dni?.message}>
          <Input inputMode="numeric" maxLength={8} autoComplete="off" {...register('dni')} />
        </Field>
        <Field label="Celular (opcional)" error={errors.telefono?.message}>
          <Input inputMode="tel" maxLength={9} autoComplete="off" {...register('telefono')} />
        </Field>
        <Field label="Nombres" error={errors.nombres?.message}>
          <Input autoComplete="off" {...register('nombres')} />
        </Field>
        <Field label="Apellidos" error={errors.apellidos?.message}>
          <Input autoComplete="off" {...register('apellidos')} />
        </Field>
        <Field label="Fecha de nacimiento (opcional)" error={errors.fechaNacimiento?.message}>
          <Input type="date" max={hoyEnLima()} {...register('fechaNacimiento')} />
        </Field>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => void registrar()} cargando={crear.isPending}>
          Registrar y seleccionar
        </Button>
        <Button size="sm" variant="fantasma" onClick={onCancelar}>
          Volver a la búsqueda
        </Button>
      </div>
    </div>
  )
}
