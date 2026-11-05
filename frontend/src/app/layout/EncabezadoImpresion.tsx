import { useSesion } from '@/shared/auth'

export function EncabezadoImpresion({ sesion: sesionProp }: { sesion?: ReturnType<typeof useSesion> }) {
  const sesionActual = useSesion()
  const sesion = sesionProp ?? sesionActual
  if (!sesion) return null

  const esVillamax = sesion.negocioActivo === 'VILLAMAX'
  const sedeObj = sesion.usuario.sedes.find((s) => s.id === sesion.sedeActivaId)
  const sedeNombre = sesion.sedeActivaId ? (sedeObj?.nombre ?? sesion.sedeActivaId) : 'Todas las sedes'
  const fechaImpresion = new Date().toLocaleString('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div
      data-testid="encabezado-impresion"
      className="hidden print:flex flex-row items-center justify-between border-b-2 border-neutral-400 pb-3 mb-6 w-full text-neutral-900 print-header"
    >
      <div className="flex items-center gap-3">
        {esVillamax ? (
          <div className="flex size-11 items-center justify-center rounded-lg bg-indigo-900 text-white font-black text-sm tracking-tighter">
            V3D
          </div>
        ) : (
          <img src="/logo-villadent-128.png" alt="Villadent" className="h-10 w-auto object-contain" />
        )}
        <div className="flex flex-col">
          <span className="font-bold text-base tracking-wide text-neutral-900">
            {esVillamax ? 'VILLAMAX 3D' : 'VILLADENT'}
          </span>
          <span className="text-xs text-neutral-600 font-medium">
            {esVillamax ? 'Centro Radiológico Maxilofacial' : 'Red Odontológica · Junín'}
          </span>
        </div>
      </div>

      <div className="text-right text-xs text-neutral-700 space-y-0.5">
        <div>
          <span className="font-semibold text-neutral-900">Sede: </span>
          <span>{sedeNombre}</span>
        </div>
        <div>
          <span className="font-semibold text-neutral-900">Negocio: </span>
          <span>{esVillamax ? 'Villamax 3D' : 'Villadent'}</span>
        </div>
        <div>
          <span className="font-semibold text-neutral-900">Fecha de impresión: </span>
          <span>{fechaImpresion}</span>
        </div>
      </div>
    </div>
  )
}
