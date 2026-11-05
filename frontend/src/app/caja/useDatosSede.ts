import { useSedesActivas } from '../agenda/api-agenda'

/** Dirección y teléfono oficiales de una sede (los que carga el administrador), para los documentos impresos. */
export function useDatosSede(sedeId: string | null | undefined) {
  const sedes = useSedesActivas()
  const sede = (sedes.data ?? []).find((s) => s.id === sedeId)
  return {
    direccion: sede?.direccion ? [sede.direccion, sede.ciudad].filter(Boolean).join(', ') : undefined,
    telefono: sede?.telefono?.replace(/\D/g, '') || sede?.whatsapp?.replace(/^51/, ''),
  }
}
