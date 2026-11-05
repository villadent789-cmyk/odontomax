import { Field, Input } from '@/design-system'
import type { CampoPlantilla, DatosFicha, PlantillaTratamiento } from './api-plantillas'

const claseSelect =
  'w-full rounded-control border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface disabled:opacity-70'

interface Props {
  plantilla: PlantillaTratamiento
  datos: DatosFicha
  onCambiar: (datos: DatosFicha) => void
  soloLectura?: boolean
}

/** Dibuja el formulario de una plantilla a partir de su esquema (campos de texto, selección, sí/no, fecha, pieza…). */
export function CamposPlantilla({ plantilla, datos, onCambiar, soloLectura = false }: Props) {
  const poner = (id: string, valor: DatosFicha[string]) => onCambiar({ ...datos, [id]: valor })

  function control(campo: CampoPlantilla) {
    const valor = datos[campo.id]
    const etiqueta = campo.etiqueta
    switch (campo.tipo) {
      case 'textoLargo':
        return (
          <textarea
            aria-label={etiqueta}
            rows={3}
            value={(valor as string) ?? ''}
            disabled={soloLectura}
            onChange={(e) => poner(campo.id, e.target.value)}
            className={claseSelect}
          />
        )
      case 'numero':
      case 'pieza':
        return (
          <Input
            aria-label={etiqueta}
            type="number"
            value={(valor as number | string | undefined) ?? ''}
            disabled={soloLectura}
            onChange={(e) => poner(campo.id, e.target.value === '' ? null : Number(e.target.value))}
          />
        )
      case 'fecha':
        return <Input aria-label={etiqueta} type="date" value={(valor as string) ?? ''} disabled={soloLectura} onChange={(e) => poner(campo.id, e.target.value)} />
      case 'seleccion':
        return (
          <select aria-label={etiqueta} value={(valor as string) ?? ''} disabled={soloLectura} onChange={(e) => poner(campo.id, e.target.value)} className={claseSelect}>
            <option value="">Elige una opción</option>
            {(campo.opciones ?? []).map((o) => (
              <option key={o} value={o}>
                {o}
              </option>
            ))}
          </select>
        )
      case 'multiple': {
        const elegidas = (Array.isArray(valor) ? valor : []) as string[]
        return (
          <div role="group" aria-label={etiqueta} className="flex flex-wrap gap-2">
            {(campo.opciones ?? []).map((o) => {
              const activa = elegidas.includes(o)
              return (
                <button
                  key={o}
                  type="button"
                  aria-pressed={activa}
                  disabled={soloLectura}
                  onClick={() => poner(campo.id, activa ? elegidas.filter((x) => x !== o) : [...elegidas, o])}
                  className={`rounded-pill border px-3 py-1 text-xs font-semibold ${activa ? 'border-acento bg-acento/15 text-primary' : 'border-outline-variant bg-surface-container-lowest text-on-surface-variant'}`}
                >
                  {o}
                </button>
              )
            })}
          </div>
        )
      }
      case 'si_no':
        return (
          <label className="flex items-center gap-2 text-sm text-on-surface">
            <input type="checkbox" aria-label={etiqueta} checked={valor === true} disabled={soloLectura} onChange={(e) => poner(campo.id, e.target.checked)} className="size-4" />
            Sí
          </label>
        )
      default:
        return <Input aria-label={etiqueta} value={(valor as string) ?? ''} disabled={soloLectura} onChange={(e) => poner(campo.id, e.target.value)} />
    }
  }

  return (
    <div className="flex flex-col gap-5">
      {plantilla.esquema.secciones.map((seccion) => (
        <section key={seccion.titulo} aria-label={seccion.titulo} className="flex flex-col gap-3">
          <h4 className="border-b border-outline-variant/60 pb-1 font-display text-sm font-bold text-primary">{seccion.titulo}</h4>
          <div className="grid gap-3 sm:grid-cols-2">
            {seccion.campos.map((campo) => (
              <div key={campo.id} className={campo.tipo === 'textoLargo' || campo.tipo === 'multiple' ? 'sm:col-span-2' : undefined}>
                {campo.tipo === 'si_no' ? (
                  <Field label={campo.etiqueta}>{control(campo)}</Field>
                ) : (
                  <Field label={`${campo.etiqueta}${campo.obligatorio ? ' *' : ''}`}>{control(campo)}</Field>
                )}
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
