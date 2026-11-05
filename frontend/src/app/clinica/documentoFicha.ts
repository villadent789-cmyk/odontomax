import { formatFecha } from '@/shared/format'
import { ESTILO_CABECERA, cabeceraImpresion } from '@/shared/cabeceraImpresion'
import type { DatosFicha, PlantillaTratamiento } from './api-plantillas'

const escapar = (texto: string | undefined) =>
  (texto ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function valorLegible(valor: DatosFicha[string]): string {
  if (valor === true) return 'Sí'
  if (valor === false) return 'No'
  if (Array.isArray(valor)) return valor.join(', ')
  if (valor === null || valor === undefined || valor === '') return '—'
  return String(valor)
}

interface Entrada {
  plantilla: PlantillaTratamiento
  datos: DatosFicha
  pacienteNombre?: string
  estado?: string
  fecha?: string
  doctor?: string | null
}

/** Documento imprimible de una ficha de tratamiento: cabecera común, datos del paciente y cada sección con sus campos. */
export function documentoFicha({ plantilla, datos, pacienteNombre, estado, fecha, doctor }: Entrada): string {
  const secciones = plantilla.esquema.secciones
    .map(
      (s) => `<h2>${escapar(s.titulo)}</h2>
<table><tbody>${s.campos
        .map((c) => `<tr><th>${escapar(c.etiqueta)}</th><td>${escapar(valorLegible(datos[c.id]))}</td></tr>`)
        .join('')}</tbody></table>`,
    )
    .join('')
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${escapar(plantilla.nombre)}</title>
<style>@page{size:A4;margin:14mm}body{font-family:Arial,sans-serif;font-size:12px;color:#111}h2{font-size:13px;margin:16px 0 4px;color:#403c36;border-bottom:1px solid #d6d2c7;padding-bottom:2px}
table{width:100%;border-collapse:collapse}th,td{padding:4px 6px;text-align:left;vertical-align:top;border-bottom:1px solid #eee}th{width:38%;font-weight:600;color:#524d46}.pie{margin-top:22px;font-size:10px;color:#7a746b}${ESTILO_CABECERA}</style></head><body>
${cabeceraImpresion({ negocio: plantilla.negocio, titulo: plantilla.nombre, subtitulo: `Ficha de tratamiento${estado === 'CERRADO' ? ' · cerrada' : ' · borrador'}` })}
<p><strong>Paciente:</strong> ${escapar(pacienteNombre)}${fecha ? ` · <strong>Fecha:</strong> ${formatFecha(fecha)}` : ''}${doctor ? ` · <strong>Doctor:</strong> ${escapar(doctor)}` : ''}</p>
${secciones}
<p class="pie">Documento clínico confidencial · Ley N.° 29733 de Protección de Datos Personales.</p>
</body></html>`
}
