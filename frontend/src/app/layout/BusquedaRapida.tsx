import { useNavigate } from '@tanstack/react-router'
import { MessageCircle, Search } from 'lucide-react'
import { useDeferredValue, useEffect, useState } from 'react'
import { FotoPaciente } from '@/app/pacientes/FotoPaciente'
import { usePacientesQuery } from '@/app/pacientes/api-pacientes'
import { Dialog, DialogContent, DialogDescription, DialogTitle, Input } from '@/design-system'

const claseBoton =
  'inline-flex size-11 items-center justify-center rounded-pill text-on-surface-variant hover:bg-surface-container-high hover:text-primary focus-visible:outline-2 focus-visible:outline-focus [&_svg]:size-5'

/** Búsqueda rápida de pacientes por nombre, DNI o celular. Se abre con el botón de la lupa o con Ctrl+K. */
export function BusquedaRapida({ sedeId }: { sedeId?: string | null }) {
  const navigate = useNavigate()
  const [abierto, setAbierto] = useState(false)
  const [texto, setTexto] = useState('')
  const busqueda = useDeferredValue(texto.trim())
  const activa = busqueda.length >= 3
  const { data = [], isFetching } = usePacientesQuery({ busqueda: activa ? busqueda : undefined, sedeId: sedeId ?? undefined, size: 8 })
  const resultados = activa ? data.filter((p) => !p.archivado).slice(0, 8) : []

  useEffect(() => {
    const alTeclear = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setAbierto(true)
      }
    }
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [])

  const cambiar = (v: boolean) => {
    setAbierto(v)
    if (!v) setTexto('')
  }

  return (
    <>
      <button type="button" onClick={() => setAbierto(true)} aria-label="Buscar paciente" title="Buscar paciente (Ctrl+K)" className={claseBoton}>
        <Search aria-hidden />
      </button>
      <Dialog open={abierto} onOpenChange={cambiar}>
        <DialogContent className="top-[8vh] max-w-xl translate-y-0">
          <DialogTitle className="font-display text-lg font-bold text-primary">Buscar paciente</DialogTitle>
          <DialogDescription className="sr-only">Busca por nombre, apellido, DNI o celular y abre su ficha.</DialogDescription>
          <Input
            icono={<Search />}
            type="search"
            autoComplete="off"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Nombre, DNI o celular"
            aria-label="Nombre, DNI o celular"
          />
          <div aria-live="polite" className="flex flex-col gap-2">
            {!activa && <p className="text-sm text-on-surface-variant">Escribe al menos 3 caracteres.</p>}
            {activa && resultados.length === 0 && !isFetching && (
              <p className="text-sm text-on-surface-variant">No se encontraron pacientes.</p>
            )}
            {resultados.map((p) => {
              const tel = p.telefono?.replace(/\D/g, '').slice(-9)
              return (
                <div key={p.id} className="flex items-center gap-2 rounded-panel border border-outline-variant/50 p-1">
                  <button
                    type="button"
                    onClick={() => {
                      cambiar(false)
                      void navigate({ to: '/app/pacientes/$id', params: { id: p.id ?? '' } })
                    }}
                    className="flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-control px-3 py-1.5 text-left hover:bg-surface-container"
                  >
                    <FotoPaciente pacienteId={p.id ?? ''} nombres={p.nombres} apellidos={p.apellidos} fotoActualizadaEn={p.fotoActualizadaEn} tamano="sm" />
                    <span className="flex min-w-0 flex-col items-start">
                      <span className="truncate font-semibold text-on-surface">{p.nombreCompleto}</span>
                      <span className="text-xs text-on-surface-variant">
                        DNI {p.dni ?? '—'}
                        {tel ? ` · ${tel}` : ''}
                      </span>
                    </span>
                  </button>
                  {tel && (
                    <a
                      href={`https://wa.me/51${tel}`}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`Escribir por WhatsApp a ${p.nombreCompleto}`}
                      className="inline-flex size-11 shrink-0 items-center justify-center rounded-pill text-emerald-700 hover:bg-emerald-50"
                    >
                      <MessageCircle aria-hidden className="size-5" />
                    </a>
                  )}
                </div>
              )
            })}
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
