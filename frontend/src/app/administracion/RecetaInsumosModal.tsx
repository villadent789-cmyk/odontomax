import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Input, toast } from '@/design-system'
import { useGuardarRecetaMutation, useProductosInventario, useRecetaServicio } from '@/app/inventario/api-inventario'
import { textoDeError } from '@/shared/http'

interface Linea {
  productoId: string
  cantidad: number
}

interface Props {
  servicio: { id: string; nombre: string }
  alCerrar: () => void
}

/** Receta de un servicio de Villadent: qué insumos y cuántas unidades gasta cada vez que se atiende. */
export function RecetaInsumosModal({ servicio, alCerrar }: Props) {
  const receta = useRecetaServicio(servicio.id)
  const productos = useProductosInventario()
  const guardar = useGuardarRecetaMutation(servicio.id)
  // Hasta que el usuario edite, se muestra la receta guardada; después, su borrador.
  const [borrador, setBorrador] = useState<Linea[] | null>(null)
  const lineas: Linea[] = borrador ?? (receta.data ?? []).map((l) => ({ productoId: l.productoId, cantidad: l.cantidad }))
  const setLineas = (cambio: (actuales: Linea[]) => Linea[]) => setBorrador(cambio(lineas))
  const [error, setError] = useState<string | null>(null)

  const catalogo = (productos.data ?? []).filter((p) => p.activo)

  function cambiar(indice: number, parcial: Partial<Linea>) {
    setLineas((actuales) => actuales.map((l, i) => (i === indice ? { ...l, ...parcial } : l)))
  }

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault()
    setError(null)
    if (lineas.some((l) => !l.productoId || !(l.cantidad >= 1))) {
      setError('Elige el insumo y una cantidad de al menos 1 en cada fila.')
      return
    }
    if (new Set(lineas.map((l) => l.productoId)).size !== lineas.length) {
      setError('Un insumo no puede repetirse en la receta.')
      return
    }
    try {
      await guardar.mutateAsync(lineas)
      toast(`${servicio.nombre}: receta guardada`)
      alCerrar()
    } catch (e) {
      setError(textoDeError(e, 'No se pudo guardar la receta.'))
    }
  }

  return (
    <Dialog open onOpenChange={(abierto) => !abierto && alCerrar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Insumos de {servicio.nombre}</DialogTitle>
          <DialogDescription>
            Al marcar la cita como atendida se descuentan del stock de la sede. Si falta stock, la atención sigue y se avisa al administrador.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={enviar} className="flex flex-col gap-4">
          {lineas.length === 0 && !receta.isPending && (
            <p className="text-sm text-on-surface-variant">Este servicio no descuenta insumos todavía.</p>
          )}
          {lineas.map((linea, i) => (
            <div key={i} className="flex items-center gap-2">
              <select
                aria-label={`Insumo ${i + 1}`}
                className="h-10 min-w-0 flex-1 rounded-control border border-outline bg-surface px-3 text-sm"
                value={linea.productoId}
                onChange={(e) => cambiar(i, { productoId: e.target.value })}
              >
                <option value="">Elige un insumo</option>
                {catalogo.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nombre}
                  </option>
                ))}
              </select>
              <Input
                aria-label={`Cantidad ${i + 1}`}
                type="number"
                min={1}
                className="w-24"
                value={linea.cantidad}
                onChange={(e) => cambiar(i, { cantidad: Number(e.target.value) })}
              />
              <Button type="button" size="sm" variant="secundario" aria-label={`Quitar insumo ${i + 1}`} onClick={() => setLineas((l) => l.filter((_, j) => j !== i))}>
                <Trash2 aria-hidden />
              </Button>
            </div>
          ))}
          <Button type="button" variant="secundario" onClick={() => setLineas((l) => [...l, { productoId: '', cantidad: 1 }])}>
            <Plus aria-hidden />
            Agregar insumo
          </Button>
          {error && (
            <p role="alert" className="text-sm text-error">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="secundario" onClick={alCerrar}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={guardar.isPending || receta.isPending}>
              {guardar.isPending ? 'Guardando…' : 'Guardar receta'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
