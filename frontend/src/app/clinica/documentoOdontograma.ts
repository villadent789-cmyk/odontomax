import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'
import { formatFecha } from '@/shared/format'
import type { PiezaInstantanea } from './api-episodios-odontograma'
import { ALTO, ANCHO, CARAS_CORONA, CONTORNO, SIN_VECINOS, decoracionDeNumero, desplazamientoCorona, primitivasASvg, primitivasDeHallazgos, rutaRaiz, vecinosDe, type Vecinos } from './dienteNorma'
import { lineasEspecificaciones } from './especificaciones'
import { buscarHerramienta, colorDeCara, type HerramientaConfig } from './herramientasOdontograma'

const esc = (t: string | number | null | undefined) =>
  String(t ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const SUPERIOR = [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28]
const INFERIOR = [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38]

export interface CapturaImpresa {
  titulo: string
  fecha: string
  piezas: PiezaInstantanea[]
  notas?: string
}

interface Datos {
  pacienteNombre: string
  pacienteDni?: string
  sedeNombre: string
  negocio?: 'VILLADENT' | 'VILLAMAX'
  /** Episodio o motivo de la comparación. */
  motivo?: string
  /** Una captura sola, o dos para ver antes y después. */
  capturas: CapturaImpresa[]
}

const nombreHallazgo = (estado?: string) => buscarHerramienta(estado)?.label ?? estado ?? 'Sin hallazgo'

/** Un diente como en la norma: contorno negro, raíz proporcional, caras pintadas solo con caries o restauración y los símbolos encima. */
export function dienteSvg(fdi: number, hallazgos: PiezaInstantanea[], vecinos: Vecinos = SIN_VECINOS): string {
  const cuadrante = Math.floor(fdi / 10)
  const superior = cuadrante === 1 || cuadrante === 2 || cuadrante === 5 || cuadrante === 6
  const derecho = cuadrante === 1 || cuadrante === 4 || cuadrante === 5 || cuadrante === 8
  const caras = {
    arriba: superior ? 'V' : 'L',
    abajo: superior ? 'P' : 'V',
    izquierda: derecho ? 'D' : 'M',
    derecha: derecho ? 'M' : 'D',
    centro: 'O',
  }
  const poligonos = (Object.keys(CARAS_CORONA) as (keyof typeof CARAS_CORONA)[])
    .map((k) => {
      const c = colorDeCara(hallazgos, caras[k])
      return `<polygon points="${CARAS_CORONA[k]}" fill="${c.fill}" stroke="${c.contorno ?? CONTORNO}" stroke-width="${c.contorno ? 7 : 3}"/>`
    })
    .join('')
  const ausente = hallazgos.some((h) => ['ausente', 'extraccion', 'exodoncia'].includes(h.estado.trim().toLowerCase()))
  const configs = hallazgos.flatMap((h) => buscarHerramienta(h.estado) ?? [])
  const simbolos = primitivasASvg(primitivasDeHallazgos(superior, ausente ? [buscarHerramienta('ausente')!] : configs, vecinos))
  return `<svg viewBox="0 0 ${ANCHO} ${ALTO}" overflow="visible"><path d="${rutaRaiz(superior)}" fill="#fff" stroke="${CONTORNO}" stroke-width="3"/><g transform="translate(0,${desplazamientoCorona(superior)})">${poligonos}</g>${simbolos}</svg>`
}

function arcada(piezas: PiezaInstantanea[]): string {
  const porPieza = new Map<number, PiezaInstantanea[]>()
  for (const p of piezas) porPieza.set(p.piezaFdi, [...(porPieza.get(p.piezaFdi) ?? []), p])
  const idsPorPieza = new Map<number, string[]>()
  for (const [fdi, lista] of porPieza) idsPorPieza.set(fdi, lista.map((h) => buscarHerramienta(h.estado)?.id ?? ''))
  const fila = (fdis: number[], numeroArriba: boolean) =>
    `<div class="fila">${fdis
      .map((fdi) => {
        const hs = porPieza.get(fdi) ?? []
        const siglas = hs
          .map((h) => {
            const t = buscarHerramienta(h.estado)
            return `<span style="color:${t?.colorHex ?? '#334155'}">${esc(t?.codigoCorto ?? h.estado)}</span>`
          })
          .join(' ')
        const deco = decoracionDeNumero(hs.flatMap((h) => buscarHerramienta(h.estado) ?? []))
        const figura = deco
          ? `<svg viewBox="0 0 40 24" fill="none" stroke="${deco.color}" stroke-width="1.8">${deco.tipo === 'triangulo' ? '<polygon points="20,0 38,23 2,23"/>' : '<circle cx="14" cy="12" r="10"/><circle cx="26" cy="12" r="10"/>'}</svg>`
          : ''
        const numero = `<b>${figura}<u>${fdi}</u></b>`
        const diente = `<span class="svg">${dienteSvg(fdi, hs, vecinosDe(fdi, idsPorPieza))}</span>`
        return `<span class="d">${numeroArriba ? numero + diente : diente + numero}<i>${siglas || '&nbsp;'}</i></span>`
      })
      .join('')}</div>`
  return `<div class="arcada">${fila(SUPERIOR, true)}${fila(INFERIOR, false)}</div>`
}

/** Los hallazgos que aparecen, cada uno con su color, para leer el odontograma sin adivinar. */
function leyenda(capturas: CapturaImpresa[]): string {
  const usados = new Map<string, HerramientaConfig>()
  for (const c of capturas) {
    for (const p of c.piezas) {
      const t = buscarHerramienta(p.estado)
      if (t) usados.set(t.id, t)
    }
  }
  if (usados.size === 0) return ''
  return `<ul class="colores">${[...usados.values()]
    .map((t) => `<li><span class="punto" style="background:${t.colorHex}"></span>${esc(t.label)} <small>${esc(t.codigoCorto)}</small></li>`)
    .join('')}</ul>`
}

/** Recuadro «Especificaciones» de la norma: explica con claridad cada hallazgo de cada pieza. */
function especificaciones(piezas: PiezaInstantanea[]): string {
  const lineas = lineasEspecificaciones(piezas.filter((p) => p.estado.trim().toLowerCase() !== 'sano'))
  const cuerpo = lineas.length
    ? lineas.map((l) => `<b>${l.piezaFdi}:</b> ${esc(l.texto)}.`).join(' ')
    : '<i>Sin hallazgos registrados.</i>'
  return `<div class="espec"><h3>Especificaciones</h3><p>${cuerpo}</p></div>`
}

function tablaHallazgos(piezas: PiezaInstantanea[]): string {
  if (piezas.length === 0) return '<p class="vacio-t">Sin hallazgos registrados.</p>'
  const filas = [...piezas]
    .sort((a, b) => a.piezaFdi - b.piezaFdi)
    .map((p) => {
      const t = buscarHerramienta(p.estado)
      const color = t?.colorHex ?? '#1b1916'
      return `<tr><td>${p.piezaFdi}</td><td style="color:${color};font-weight:600">${esc(nombreHallazgo(p.estado))}</td><td>${esc((p.caras ?? []).join(', ') || '—')}</td></tr>`
    })
    .join('')
  return `<table><thead><tr><th>Pieza</th><th>Hallazgo</th><th>Caras</th></tr></thead><tbody>${filas}</tbody></table>`
}

function diferencias(antes: PiezaInstantanea[], despues: PiezaInstantanea[]): string {
  const filas: string[] = []
  const piezas = [...new Set([...antes, ...despues].map((p) => p.piezaFdi))].sort((x, y) => x - y)
  for (const fdi of piezas) {
    const a = antes.filter((p) => p.piezaFdi === fdi).map((p) => p.estado).sort()
    const d = despues.filter((p) => p.piezaFdi === fdi).map((p) => p.estado).sort()
    if (a.join() === d.join()) continue
    const nombres = (estados: string[]) => estados.map(nombreHallazgo).join(', ') || 'Sin hallazgo'
    filas.push(`<tr><td>${fdi}</td><td>${esc(nombres(a))}</td><td><b>${esc(nombres(d))}</b></td></tr>`)
  }
  return filas.length
    ? `<table><thead><tr><th>Pieza</th><th>Antes</th><th>Después</th></tr></thead><tbody>${filas.join('')}</tbody></table>`
    : '<p class="vacio-t">No hay diferencias entre las dos fechas.</p>'
}

/**
 * Odontograma imprimible o para guardar en PDF: el del paciente en una fecha o, con dos capturas, el antes y el después
 * lado a lado con las piezas que cambiaron. Rojo: caries, fractura, temporales y mal estado. Azul: lo demás (norma del odontograma).
 */
export function documentoOdontograma({ pacienteNombre, pacienteDni, sedeNombre, negocio = 'VILLADENT', motivo, capturas }: Datos): string {
  const comparando = capturas.length === 2
  const columnas = capturas
    .map(
      (c) =>
        `<section class="col"><h2>${esc(c.titulo)} · ${formatFecha(c.fecha)}</h2>${arcada(c.piezas)}${especificaciones(c.piezas)}${tablaHallazgos(c.piezas)}${c.notas ? `<p class="nota">${esc(c.notas)}</p>` : ''}</section>`,
    )
    .join('')
  const ancho = 38 // la corona mide ~1 cm² (regla de la norma), también al comparar
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Odontograma</title>
<style>@page{size:A4 portrait;margin:12mm}
body{font-family:Arial,sans-serif;font-size:11px;color:#1b1916;line-height:1.4;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.datos{display:flex;gap:24px;flex-wrap:wrap;margin:0 0 10px}.datos b{color:#524d46}
.cols{display:grid;grid-template-columns:1fr;gap:18px}
h2{font-size:13px;margin:0 0 6px;color:#0f172a}
.arcada{display:flex;flex-direction:column;gap:3px;margin-bottom:8px}.fila{display:flex;gap:2px;justify-content:center}
.d{display:flex;flex-direction:column;align-items:center;gap:1px;width:${ancho}px;font-size:11px}
.d b{position:relative;display:inline-block;font-weight:700;color:#334155;padding:0 4px}.d b u{text-decoration:none;position:relative}.d b svg{position:absolute;left:50%;top:50%;width:26px;height:16px;transform:translate(-50%,-50%)}.d i{font-style:normal;font-weight:700;font-size:8px;line-height:1;width:100%;box-sizing:border-box;text-align:center;border:1px solid #27272a;background:#fff;padding:1px 0;min-height:12px;overflow:hidden}
.d .svg{display:block;width:${ancho}px;height:${Math.round(ancho * 1.65)}px}.d svg{width:100%;height:100%;display:block}
.colores{display:flex;flex-wrap:wrap;gap:3px 14px;list-style:none;padding:0;margin:0 0 8px;font-size:10px}.colores li{display:flex;align-items:center;gap:4px}.colores small{color:#7a746b}.punto{display:inline-block;width:9px;height:9px;border-radius:50%}
table{width:100%;border-collapse:collapse;margin-top:6px}th,td{padding:3px 5px;text-align:left;border-bottom:1px solid #e8e5dd}th{background:#f4f2ed;font-size:10px}
.leyenda{margin:8px 0;font-size:10px;color:#524d46}.rojo{color:#B91C1C;font-weight:700}.azul{color:#1D4ED8;font-weight:700}
.vacio-t{color:#7a746b;font-style:italic}.nota{margin-top:6px;font-style:italic;color:#524d46}
.espec{border:1px solid #27272a;padding:6px 8px;margin:6px 0 8px;font-size:10px;line-height:1.45}.espec h3{margin:0 0 3px;font-size:9px;text-transform:uppercase;letter-spacing:.06em}.espec p{margin:0}
.firma{display:flex;gap:32px;margin-top:26px}.firma div{flex:1;border-top:1px solid #27272a;padding-top:3px;text-align:center;font-size:10px;color:#334155}
.pie{margin-top:14px;border-top:1px solid #d6d2c7;padding-top:6px;font-size:9px;color:#7a746b}
${ESTILO_CABECERA}</style></head><body>
${cabeceraImpresion({ negocio, titulo: comparando ? 'Odontograma: antes y después' : 'Odontograma', subtitulo: sedeNombre })}
<div class="datos"><div><b>Paciente:</b> ${esc(pacienteNombre)}</div><div><b>DNI:</b> ${esc(pacienteDni ?? '—')}</div>${motivo ? `<div><b>Motivo:</b> ${esc(motivo)}</div>` : ''}</div>
<p class="leyenda"><span class="rojo">Rojo</span>: caries, fractura, remanente radicular, temporales y tratamientos en mal estado · <span class="azul">Azul</span>: tratamientos en buen estado o realizados y anomalías.</p>
${leyenda(capturas)}
<div class="cols">${columnas}</div>
<div class="firma"><div>Firma y sello del profesional responsable</div><div>Fecha</div></div>
${comparando ? `<h2 style="margin-top:12px">Piezas que cambiaron</h2>${diferencias(capturas[0].piezas, capturas[1].piezas)}` : ''}
<p class="pie">Documento generado desde el sistema. Información confidencial del paciente. Colores y siglas según la Norma Técnica del Odontograma: solo rojo y azul.</p>
</body></html>`
}
