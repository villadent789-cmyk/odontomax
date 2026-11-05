import { useState } from 'react'
import { Alert, Button } from '@/design-system'
import { formatFecha } from '@/shared/format'
import { textoDeError } from '@/shared/http'
import { useAlertasConsumo, useResolverAlertaConsumoMutation } from './api-inventario'

interface Props {
  sedeId?: string
}

/** Avisa al administrador de las atenciones que gastaron más insumo del que había; no bloquea nada. */
export function AlertasConsumo({ sedeId }: Props) {
  const alertas = useAlertasConsumo(sedeId)
  const resolver = useResolverAlertaConsumoMutation()
  const [error, setError] = useState<string | null>(null)

  if (!alertas.data || alertas.data.length === 0) return null

  async function marcarResuelta(id: string) {
    setError(null)
    try {
      await resolver.mutateAsync(id)
    } catch (e) {
      setError(textoDeError(e, 'No se pudo marcar la alerta como resuelta.'))
    }
  }

  return (
    <Alert tono="aviso" titulo="Insumos agotados al atender">
      <p>
        Estas atenciones gastaron más insumo del que había en stock. El stock quedó en cero: repóngalo con una entrada y
        marque la alerta como resuelta.
      </p>
      <ul className="mt-2 flex flex-col gap-2">
        {alertas.data.map((a) => (
          <li key={a.id} className="flex flex-wrap items-center justify-between gap-2">
            <span>
              <strong>{a.productoNombre}</strong>: faltaron {a.faltante} de {a.cantidadRequerida}
              {a.motivoCita ? ` · ${a.motivoCita}` : ''}
              {a.fechaCita ? ` · ${formatFecha(a.fechaCita)}` : ''}
            </span>
            <Button size="sm" variant="secundario" disabled={resolver.isPending} onClick={() => void marcarResuelta(a.id)}>
              Marcar resuelta
            </Button>
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="mt-2">
          {error}
        </p>
      )}
    </Alert>
  )
}
