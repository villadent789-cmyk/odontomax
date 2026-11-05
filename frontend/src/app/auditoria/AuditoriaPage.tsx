import { RefreshCw, Search, ShieldCheck } from 'lucide-react'
import { useId, useMemo, useState } from 'react'
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  EmptyState,
  ErrorState,
  Input,
  Skeleton,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { NOMBRE_ROL, esRol } from '@/shared/auth'
import { formatFecha } from '@/shared/format'
import { mensajeError } from '@/shared/errores'
import { useSedesActivas } from '../agenda/api-agenda'
import { TODOS_LOS_MODULOS, useAuditoria, type AuditoriaDTO } from './api-auditoria'
import {
  MODULOS,
  SIN_SEDE,
  detalleLegible,
  etiquetaModulo,
  filtrarEventos,
  paginar,
  tonoModulo,
  tonoRol,
} from './filtros'

const nombreRol = (rol: string | undefined) => (esRol(rol) ? NOMBRE_ROL[rol] : (rol ?? '—'))

/** Módulo 7 · Auditoría global: quién hizo qué y cuándo. Solo ADMIN_GENERAL. */
export function AuditoriaPage() {
  const uid = useId()
  const [modulo, setModulo] = useState(TODOS_LOS_MODULOS)
  const [texto, setTexto] = useState('')
  const [sede, setSede] = useState('')
  const [fecha, setFecha] = useState('')
  const [pagina, setPagina] = useState(1)
  const [seleccionado, setSeleccionado] = useState<AuditoriaDTO | null>(null)

  const auditoria = useAuditoria(modulo)
  const sedesActivas = useSedesActivas()
  const nombreSede = (id: string | undefined) =>
    !id ? 'General' : (sedesActivas.data?.find((s) => s.id === id)?.nombre ?? id)

  // Sedes del selector: las activas más cualquiera que aparezca en los eventos (p. ej. una sede ya desactivada).
  const opcionesSede = useMemo(() => {
    const mapa = new Map<string, string>()
    for (const s of sedesActivas.data ?? []) if (s.id) mapa.set(s.id, s.nombre ?? s.id)
    for (const e of auditoria.data ?? []) if (e.sedeId && !mapa.has(e.sedeId)) mapa.set(e.sedeId, e.sedeId)
    return [...mapa].map(([id, nombre]) => ({ id, nombre }))
  }, [sedesActivas.data, auditoria.data])

  const eventos = useMemo(() => auditoria.data ?? [], [auditoria.data])
  const filtrados = useMemo(() => filtrarEventos(eventos, { texto, sede, fecha }), [eventos, texto, sede, fecha])
  const vista = paginar(filtrados, pagina)
  const hayFiltros = modulo !== TODOS_LOS_MODULOS || texto.trim() !== '' || sede !== '' || fecha !== ''

  const cambiar = <T,>(poner: (v: T) => void) => (valor: T) => {
    poner(valor)
    setPagina(1)
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-headline-lg font-bold">Auditoría del Sistema</h1>
          <p className="text-on-surface-variant">Registro cronológico de operaciones, accesos y cambios críticos</p>
        </div>
        <Button variant="secundario" onClick={() => void auditoria.refetch()} cargando={auditoria.isFetching && !auditoria.isPending}>
          <RefreshCw aria-hidden />
          Refrescar
        </Button>
      </header>

      <section aria-label="Filtros" className="grid gap-3 rounded-card bg-surface-container-lowest p-4 shadow-card md:grid-cols-2 xl:grid-cols-4">
        <label className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
          Módulo
          <select className={inputClases} value={modulo} onChange={(e) => cambiar(setModulo)(e.target.value)}>
            <option value={TODOS_LOS_MODULOS}>Todos los módulos</option>
            {MODULOS.map((m) => (
              <option key={m.valor} value={m.valor}>
                {m.etiqueta}
              </option>
            ))}
          </select>
        </label>
        <label htmlFor={`${uid}-buscar`} className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
          Buscar
          <Input
            id={`${uid}-buscar`}
            icono={<Search />}
            type="search"
            value={texto}
            onChange={(e) => cambiar(setTexto)(e.target.value)}
            placeholder="Usuario, detalle, acción o IP"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
          Sede
          <select className={inputClases} value={sede} onChange={(e) => cambiar(setSede)(e.target.value)}>
            <option value="">Todas las sedes</option>
            {opcionesSede.map((s) => (
              <option key={s.id} value={s.id}>
                {s.nombre}
              </option>
            ))}
            <option value={SIN_SEDE}>General (sin sede)</option>
          </select>
        </label>
        <label htmlFor={`${uid}-fecha`} className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
          Fecha
          <Input id={`${uid}-fecha`} type="date" value={fecha} onChange={(e) => cambiar(setFecha)(e.target.value)} />
        </label>
      </section>

      <p className="text-xs text-on-surface-variant">
        Se muestran los 200 eventos más recientes{modulo !== TODOS_LOS_MODULOS ? ` del módulo ${etiquetaModulo(modulo)}` : ''}; la
        búsqueda, la sede y la fecha filtran sobre ellos.
      </p>

      <section aria-label="Eventos registrados" className="rounded-card bg-surface-container-lowest shadow-card">
        {auditoria.isPending ? (
          <div role="status" aria-label="Cargando eventos" className="flex flex-col gap-3 p-6">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : auditoria.isError ? (
          <ErrorState
            titulo="No pudimos cargar la auditoría"
            descripcion={mensajeError(auditoria.error)}
            onReintentar={() => void auditoria.refetch()}
          />
        ) : filtrados.length === 0 ? (
          <EmptyState
            icono={<ShieldCheck />}
            titulo={hayFiltros ? 'No se encontraron eventos con los filtros aplicados' : 'Aún no hay eventos registrados'}
            descripcion={hayFiltros ? 'Prueba con otro texto, sede, fecha o módulo.' : undefined}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[60rem] text-left text-sm">
                <caption className="sr-only">Eventos de auditoría, del más reciente al más antiguo</caption>
                <thead className="text-on-surface-variant">
                  <tr className="border-b border-outline-variant/40">
                    <th scope="col" className="px-4 py-3 font-bold">Fecha / Hora</th>
                    <th scope="col" className="px-4 py-3 font-bold">Usuario y rol</th>
                    <th scope="col" className="px-4 py-3 font-bold">Sede</th>
                    <th scope="col" className="px-4 py-3 font-bold">Módulo / Acción</th>
                    <th scope="col" className="px-4 py-3 font-bold">Detalle</th>
                    <th scope="col" className="px-4 py-3 font-bold">IP</th>
                  </tr>
                </thead>
                <tbody>
                  {vista.items.map((e) => (
                    <tr
                      key={e.id}
                      onClick={() => setSeleccionado(e)}
                      className="cursor-pointer border-b border-outline-variant/20 last:border-0 hover:bg-surface-container-low"
                    >
                      <td className="px-4 py-3 whitespace-nowrap">
                        <button
                          type="button"
                          className="text-left font-bold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
                          aria-label={`Ver detalle: ${e.accion ?? 'evento'} el ${formatFecha(e.fecha)} a las ${e.hora?.slice(0, 5)}`}
                          onClick={(ev) => {
                            ev.stopPropagation()
                            setSeleccionado(e)
                          }}
                        >
                          {formatFecha(e.fecha)}
                        </button>
                        <span className="block text-xs text-on-surface-variant">{e.hora ?? '—'}</span>
                      </td>
                      <td className="px-4 py-3">
                        <span className="block font-bold text-on-surface">{e.usuarioNombre ?? '—'}</span>
                        <Badge tono={tonoRol(e.rol)}>{nombreRol(e.rol)}</Badge>
                      </td>
                      <td className="px-4 py-3">{nombreSede(e.sedeId)}</td>
                      <td className="px-4 py-3">
                        <Badge tono={tonoModulo(e.modulo)}>{etiquetaModulo(e.modulo)}</Badge>
                        <span className="mt-1 block font-mono text-xs">{e.accion}</span>
                      </td>
                      <td className="max-w-md px-4 py-3">
                        <span className="line-clamp-2">{e.detalle}</span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">{e.ip || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant/40 px-4 py-3 text-sm">
              <p aria-live="polite">
                Página {vista.pagina} de {vista.totalPaginas} · {filtrados.length} {filtrados.length === 1 ? 'evento' : 'eventos'}
              </p>
              <div className="flex gap-2">
                <Button size="sm" variant="secundario" disabled={vista.pagina <= 1} onClick={() => setPagina(vista.pagina - 1)}>
                  Anterior
                </Button>
                <Button size="sm" variant="secundario" disabled={vista.pagina >= vista.totalPaginas} onClick={() => setPagina(vista.pagina + 1)}>
                  Siguiente
                </Button>
              </div>
            </nav>
          </>
        )}
      </section>

      <DetalleEvento evento={seleccionado} nombreSede={nombreSede} alCerrar={() => setSeleccionado(null)} />
    </div>
  )
}

function DetalleEvento({
  evento,
  nombreSede,
  alCerrar,
}: {
  evento: AuditoriaDTO | null
  nombreSede: (id: string | undefined) => string
  alCerrar: () => void
}) {
  const detalle = detalleLegible(evento?.detalle)
  const filas: [string, string | undefined][] = evento
    ? [
        ['ID del evento', evento.id],
        ['Fecha y hora', `${formatFecha(evento.fecha)} ${evento.hora ?? ''}`.trim()],
        ['Usuario', `${evento.usuarioNombre ?? '—'} (${nombreRol(evento.rol)})`],
        ['ID del usuario', evento.usuarioId],
        ['Sede', nombreSede(evento.sedeId)],
        ['Módulo', etiquetaModulo(evento.modulo)],
        ['Acción', evento.accion],
        ['Tabla afectada', evento.tabla],
        ['ID del registro', evento.registroId],
        ['IP', evento.ip],
      ]
    : []
  return (
    <Dialog open={!!evento} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Detalle del evento</DialogTitle>
          <DialogDescription>Registro de auditoría de solo lectura.</DialogDescription>
        </DialogHeader>
        <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
          {filas.map(([titulo, valor]) => (
            <div key={titulo} className="contents">
              <dt className="text-on-surface-variant">{titulo}</dt>
              <dd className="break-all font-bold text-on-surface">{valor || '—'}</dd>
            </div>
          ))}
        </dl>
        <section aria-label="Detalle completo" className="flex flex-col gap-1">
          <h3 className="text-sm font-bold text-on-surface-variant">Detalle</h3>
          <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded-panel bg-surface-container-low p-3 font-mono text-xs">
            {detalle.texto || '—'}
          </pre>
        </section>
      </DialogContent>
    </Dialog>
  )
}
