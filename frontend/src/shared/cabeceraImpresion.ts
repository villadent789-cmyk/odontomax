/**
 * Cabecera común de los documentos impresos (reporte del día, estado de cuenta, ficha de tratamiento…):
 * logo y nombre del negocio a la izquierda, título y sede a la derecha, filete dorado debajo.
 * Todo lo que viene del servidor se escapa.
 */

const escapar = (texto: string | undefined) =>
  (texto ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

export const ESTILO_CABECERA = `
.cab{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:10px;margin-bottom:14px;border-bottom:2px solid #ac8449}
.cab-marca{display:flex;align-items:center;gap:10px}
.cab-logo{width:46px;height:46px;border-radius:50%;object-fit:contain}
.cab-nombre{font-size:16px;font-weight:700;letter-spacing:.02em;color:#0f172a}
.cab-rubro{font-size:10px;letter-spacing:.14em;text-transform:uppercase;color:#7a746b}
.cab-doc{text-align:right}
.cab-titulo{font-size:15px;font-weight:700;color:#0f172a}
.cab-sub{font-size:11px;color:#524d46}
`

interface Datos {
  negocio?: 'VILLADENT' | 'VILLAMAX'
  titulo: string
  subtitulo?: string
}

export function cabeceraImpresion({ negocio = 'VILLADENT', titulo, subtitulo }: Datos): string {
  const esVillamax = negocio === 'VILLAMAX'
  const logo = esVillamax
    ? ''
    : `<img class="cab-logo" src="${typeof location !== 'undefined' ? location.origin : ''}/logo-villadent-128.png" alt="">`
  return `<header class="cab">
<div class="cab-marca">${logo}<div><div class="cab-nombre">${esVillamax ? 'VILLAMAX 3D' : 'VILLADENT'}</div><div class="cab-rubro">${esVillamax ? 'Centro radiológico' : 'Red odontológica'}</div></div></div>
<div class="cab-doc"><div class="cab-titulo">${escapar(titulo)}</div>${subtitulo ? `<div class="cab-sub">${escapar(subtitulo)}</div>` : ''}</div>
</header>`
}
