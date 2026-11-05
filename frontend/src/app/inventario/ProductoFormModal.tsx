import { textoDeError } from '@/shared/http'
import { useState, useId } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { AlertCircle } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/design-system/components/Dialog'
import {
  useCrearProductoMutation,
  useActualizarProductoMutation,
  type ProductoInventarioDTO,
  type ProductoInventarioCreateRequestDTO,
  type ProductoInventarioUpdateRequestDTO,
} from './api-inventario'

interface ProductoFormModalProps {
  abierto: boolean
  onCerrar: () => void
  productoEditar?: ProductoInventarioDTO | null
}

const CATEGORIAS_COMUNES = [
  'Restauración y Operatoria',
  'Anestésicos y Farmacología',
  'Bioseguridad y Protección',
  'Ortodoncia Especializada',
  'Implantología Quirúrgica',
  'Endodoncia',
  'Materiales de Impresión',
  'Radiología e Imagenología',
  'Instrumental Odontológico',
  'Otros Insumos',
]

const UNIDADES_COMUNES = [
  'Jeringa 4g',
  'Caja x 50 cartuchos',
  'Caja x 100 uds',
  'Kit 5x5',
  'Unidad estéril',
  'Frasco 100ml',
  'Bolsa x 500g',
  'Paquete',
  'Rollo',
  'Pieza',
]

export function ProductoFormModal({
  abierto,
  onCerrar,
  productoEditar,
}: ProductoFormModalProps) {
  const uid = useId()
  const esEdicion = Boolean(productoEditar)

  const [codigo, setCodigo] = useState('')
  const [nombre, setNombre] = useState('')
  const [categoria, setCategoria] = useState('')
  const [unidad, setUnidad] = useState('')
  const [precioUnitario, setPrecioUnitario] = useState('')
  const [stockMinimo, setStockMinimo] = useState('5')
  const [errorValidacion, setErrorValidacion] = useState<string | null>(null)

  const crearMutation = useCrearProductoMutation()
  const actualizarMutation = useActualizarProductoMutation()

  useAlCambiar([abierto, productoEditar?.id], () => {
    if (abierto) {
      if (productoEditar) {
        setCodigo(productoEditar.codigo)
        setNombre(productoEditar.nombre)
        setCategoria(productoEditar.categoria || '')
        setUnidad(productoEditar.unidad || '')
        setPrecioUnitario(productoEditar.precio_unitario != null ? String(productoEditar.precio_unitario) : '')
        setStockMinimo(String(productoEditar.stock_minimo))
      } else {
        setCodigo('')
        setNombre('')
        setCategoria(CATEGORIAS_COMUNES[0])
        setUnidad(UNIDADES_COMUNES[0])
        setPrecioUnitario('')
        setStockMinimo('5')
      }
      setErrorValidacion(null)
    }
  })

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorValidacion(null)

    if (!nombre.trim()) {
      setErrorValidacion('El nombre del insumo es obligatorio.')
      return
    }

    const stockMinNum = parseInt(stockMinimo, 10)
    if (isNaN(stockMinNum) || stockMinNum < 0) {
      setErrorValidacion('El stock mínimo no puede ser negativo.')
      return
    }

    const precioNum = precioUnitario ? parseFloat(precioUnitario) : undefined
    if (precioNum !== undefined && (isNaN(precioNum) || precioNum < 0)) {
      setErrorValidacion('El precio unitario no puede ser negativo.')
      return
    }

    try {
      if (esEdicion && productoEditar) {
        const dto: ProductoInventarioUpdateRequestDTO = {
          nombre: nombre.trim(),
          categoria: categoria.trim() || undefined,
          unidad: unidad.trim() || undefined,
          precio_unitario: precioNum,
          stock_minimo: stockMinNum,
        }
        await actualizarMutation.mutateAsync({ id: productoEditar.id, dto })
      } else {
        const dto: ProductoInventarioCreateRequestDTO = {
          codigo: codigo.trim() || undefined,
          nombre: nombre.trim(),
          categoria: categoria.trim() || undefined,
          unidad: unidad.trim() || undefined,
          precio_unitario: precioNum,
          stock_minimo: stockMinNum,
        }
        await crearMutation.mutateAsync(dto)
      }
      onCerrar()
    } catch (err) {
      setErrorValidacion(textoDeError(err, 'Error al guardar el insumo en el catálogo.'))
    }
  }

  const guardando = crearMutation.isPending || actualizarMutation.isPending

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>
            {esEdicion ? 'Editar Insumo Odontológico' : 'Nuevo Insumo en Catálogo Maestro'}
          </DialogTitle>
          <DialogDescription>
            {esEdicion
              ? 'Actualiza los datos del producto compartido entre todas las sedes'
              : 'Registra un insumo en el catálogo clínico centralizado'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 py-2">
          {errorValidacion && (
            <div className="flex items-center gap-2 rounded-xl border border-error/30 bg-error-container/20 p-3 text-body-sm text-error">
              <AlertCircle className="size-4 shrink-0" />
              <span>{errorValidacion}</span>
            </div>
          )}

          {/* Código (sólo editable al crear o autogenerado) */}
          <div className="flex flex-col gap-1.5">
            <label className="text-label-md font-semibold text-on-surface">
              Código Insumo {!esEdicion && <span className="text-on-surface-variant font-normal">(Opcional, autogenerado si se omite)</span>}
            </label>
            <input
              type="text"
              placeholder="Ej. MAT-006"
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.toUpperCase())}
              disabled={esEdicion}
              className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 font-mono text-body-sm text-on-surface disabled:bg-surface-container focus:border-primary focus:outline-none"
            />
          </div>

          {/* Nombre */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${uid}-nombre-comercial-y-especificacion`} className="text-label-md font-semibold text-on-surface">
              Nombre Comercial y Especificación <span className="text-error">*</span>
            </label>
            <input
              id={`${uid}-nombre-comercial-y-especificacion`}
              type="text"
              placeholder="Ej. Resina Filtek Z350 XT (A1), Anestesia Mepivacaína 3%..."
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
              required
            />
          </div>

          {/* Categoría */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${uid}-categoria-clinica`} className="text-label-md font-semibold text-on-surface">
              Categoría Clínica
            </label>
            <input
              id={`${uid}-categoria-clinica`}
              type="text"
              list="lista-categorias-sugeridas"
              placeholder="Seleccione o escriba..."
              value={categoria}
              onChange={(e) => setCategoria(e.target.value)}
              className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
            />
            <datalist id="lista-categorias-sugeridas">
              {CATEGORIAS_COMUNES.map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
          </div>

          {/* Unidad / Presentación */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${uid}-presentacion-unidad-de-medida`} className="text-label-md font-semibold text-on-surface">
              Presentación / Unidad de Medida
            </label>
            <input
              id={`${uid}-presentacion-unidad-de-medida`}
              type="text"
              list="lista-unidades-sugeridas"
              placeholder="Ej. Jeringa 4g, Caja x 50..."
              value={unidad}
              onChange={(e) => setUnidad(e.target.value)}
              className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
            />
            <datalist id="lista-unidades-sugeridas">
              {UNIDADES_COMUNES.map((u) => (
                <option key={u} value={u} />
              ))}
            </datalist>
          </div>

          {/* Stock Mínimo y Precio Unitario */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-stock-minimo-alerta`} className="text-label-md font-semibold text-on-surface">
                Stock Mínimo Alerta <span className="text-error">*</span>
              </label>
              <input
                id={`${uid}-stock-minimo-alerta`}
                type="number"
                min="0"
                step="1"
                value={stockMinimo}
                onChange={(e) => setStockMinimo(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-precio-unit-ref-s`} className="text-label-md font-semibold text-on-surface">
                Precio Unit. Ref (S/)
              </label>
              <input
                id={`${uid}-precio-unit-ref-s`}
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00"
                value={precioUnitario}
                onChange={(e) => setPrecioUnitario(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
              />
            </div>
          </div>

          <DialogFooter className="mt-2">
            <Button variant="fantasma" type="button" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button variant="primario" type="submit" disabled={guardando}>
              {guardando ? 'Guardando...' : esEdicion ? 'Actualizar Insumo' : 'Crear Insumo'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
