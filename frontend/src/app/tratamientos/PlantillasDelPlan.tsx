import { Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button, Field, Input, toast } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { textoDeError } from '@/shared/http'
import { useQuitarPlantillaPlan, type PlantillaPlan } from './api-plantillas-plan'

const GRAVEDAD: Record<string, string> = { LEVE: 'leve', MODERADO: 'moderado', SEVERO: 'severo' }

/** «Usar una plantilla»: precarga el plan con el servicio, las piezas, la gravedad y las sesiones guardadas. */
export function SelectorPlantilla({ plantillas, deshabilitado, alElegir }: { plantillas: PlantillaPlan[]; deshabilitado: boolean; alElegir: (p: PlantillaPlan) => void }) {
  const sesion = useSesion()
  const quitar = useQuitarPlantillaPlan()
  const [elegida, setElegida] = useState('')
  if (plantillas.length === 0) return null
  const actual = plantillas.find((p) => p.id === elegida)
  const puedeQuitar = sesion?.usuario.rol !== 'ODONTOLOGO'

  return (
    <Field label="Usar una plantilla" ayuda="Planes que ya armaste antes. El precio sale del catálogo de hoy.">
      <div className="flex gap-2">
        <select
          aria-label="Usar una plantilla"
          value={elegida}
          disabled={deshabilitado}
          onChange={(evento) => {
            setElegida(evento.target.value)
            const p = plantillas.find((x) => x.id === evento.target.value)
            if (p) alElegir(p)
          }}
          className="w-full rounded-md border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
        >
          <option value="">Empezar desde cero</option>
          {plantillas.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nombre} · {p.piezas} {p.piezas === 1 ? 'pieza' : 'piezas'}
              {p.gravedad ? ` ${GRAVEDAD[p.gravedad]}` : ''} · {p.sesiones} {p.sesiones === 1 ? 'sesión' : 'sesiones'}
            </option>
          ))}
        </select>
        {actual && puedeQuitar && !deshabilitado && (
          <Button
            type="button"
            variant="secundario"
            size="sm"
            aria-label={`Quitar la plantilla ${actual.nombre}`}
            disabled={quitar.isPending}
            onClick={() =>
              quitar.mutate(actual.id!, {
                onSuccess: () => { setElegida(''); toast(`Plantilla «${actual.nombre}» quitada`) },
                onError: (e) => toast.error(textoDeError(e, 'No se pudo quitar la plantilla.')),
              })
            }
          >
            <Trash2 aria-hidden />
          </Button>
        )}
      </div>
    </Field>
  )
}

/** Con un nombre escrito, al crear el plan también se guarda como plantilla para la próxima vez. */
export function GuardarComoPlantilla({ valor, alCambiar }: { valor: string; alCambiar: (v: string) => void }) {
  return (
    <Field label="Guardar como plantilla (opcional)" ayuda="Escribe un nombre para reutilizar este plan: servicio, piezas, gravedad y sesiones.">
      <Input value={valor} onChange={(evento) => alCambiar(evento.target.value)} maxLength={120} placeholder="Ej. Curación de molares, 3 piezas" />
    </Field>
  )
}
