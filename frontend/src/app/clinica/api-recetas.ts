import { useMutation } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'

export type CuerpoReceta = Esquema<'RecetaRequestDTO'>
export type MedicamentoReceta = Esquema<'ItemRecetaDTO'>

/** POST /recetas/pdf: devuelve el PDF oficial como Blob (el servidor no guarda la receta). */
export function useGenerarRecetaPdf() {
  return useMutation({
    mutationFn: (cuerpo: CuerpoReceta) => apiTipada.post('/recetas/pdf', { cuerpo, binario: true }),
  })
}
