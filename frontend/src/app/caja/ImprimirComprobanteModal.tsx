import { useState } from 'react'
import { Printer, MessageCircle, FileText, CheckCircle2 } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import { Alert } from '@/design-system/components/Alert'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/design-system/components/Dialog'
import { Field } from '@/design-system/components/Field'
import { Input } from '@/design-system/components/Input'
import { formatSoles, formatFecha } from '@/shared/format'
import { useSesion } from '@/shared/auth'
import { imprimirDocumento } from '@/shared/imprimir'
import type { TratamientoDTO } from '../tratamientos/api-tratamientos'

interface Props {
  abierto: boolean
  alCerrar: () => void
  tratamiento: TratamientoDTO | null
  pacienteNombre?: string
  pacienteDni?: string
  sedeNombre?: string
  sedeDireccion?: string
  sedeTelefono?: string
  negocio?: 'VILLADENT' | 'VILLAMAX'
  /** Recibos reales de los cobros del plan (p. ej. R001-000012); se muestran en el documento. */
  comprobantes?: string[]
  /** Medios con que se cobró (p. ej. «Tarjeta de crédito»); se imprimen en el comprobante. */
  mediosDePago?: string[]
}

interface HtmlComprobanteParams {
  nombreEmpresa: string
  rubroEmpresa: string
  nombreSede: string
  direccionSede: string
  telefonoSede: string
  tipoComprobante: 'BOLETA' | 'FACTURA'
  serieCorrelativo: string
  razonSocial: string
  numeroDoc: string
  tipoDocLabel: string
  direccionCliente: string
  conceptoNombre: string
  conceptoLabel: string
  fechaEmision: string
  doctorNombre: string
  etiquetaDoctor: string
  items: Array<{
    nombre: string
    pieza: string
    cantidad: number
    precioUnitario: number
    importe: number
  }>
  modalidadPago: string
  mediosDePago: string
  faltaPagar: boolean
  mensajeBloqueo: string
  desglosarIgv: boolean
  subtotal: number
  subtotalGravado: number
  descuento: number
  igvCalculado: number
  total: number
  pagado: number
  saldoPendiente: number
}

function generarHtmlComprobante(params: HtmlComprobanteParams): string {
  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>Comprobante de pago - ${params.serieCorrelativo}</title>
  <style>
    @page { size: A4 portrait; margin: 12mm 15mm; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      font-size: 10pt;
      line-height: 1.35;
      color: #111827;
      background: #ffffff;
      padding: 0;
      margin: 0 auto;
      width: 100%;
      max-width: 100%;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .comprobante-wrapper {
      width: 100%;
      margin: 0 auto;
      border: 1px solid #d1d5db;
      border-radius: 8px;
      padding: 24px;
    }
    @media print {
      body { margin: 0; padding: 0; }
      .comprobante-wrapper { border: none; padding: 0; border-radius: 0; }
    }
    .header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      border-bottom: 1px solid #e5e7eb;
      padding-bottom: 14px;
      margin-bottom: 14px;
    }
    .empresa-titulo {
      font-size: 15pt;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: #134e4a;
    }
    .empresa-rubro {
      font-size: 8.5pt;
      font-weight: 600;
      color: #4b5563;
      margin-top: 2px;
    }
    .empresa-datos {
      font-size: 8pt;
      color: #6b7280;
      margin-top: 4px;
      line-height: 1.3;
    }
    .box-comprobante {
      border: 2px solid #1f2937;
      border-radius: 6px;
      padding: 8px 16px;
      text-align: center;
      min-width: 210px;
      background: #f9fafb;
    }
    .box-ruc { font-size: 8.5pt; font-weight: 700; color: #374151; }
    .box-tipo { font-size: 10.5pt; font-weight: 900; color: #111827; margin: 4px 0; text-transform: uppercase; }
    .box-serie { font-size: 9pt; font-family: monospace; font-weight: 700; color: #111827; }
    .datos-cliente {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      font-size: 8.5pt;
      color: #374151;
      margin-bottom: 14px;
      line-height: 1.5;
    }
    .tabla-items {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 14px;
      font-size: 8.5pt;
    }
    .tabla-items th {
      background: #f3f4f6;
      border: 1px solid #e5e7eb;
      padding: 7px 10px;
      font-weight: 600;
      text-align: left;
    }
    .tabla-items td {
      border: 1px solid #e5e7eb;
      padding: 6px 10px;
    }
    .text-center { text-align: center; }
    .text-right { text-align: right; }
    .font-mono { font-family: monospace; }
    .totales-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-top: 6px;
    }
    .condiciones {
      font-size: 8pt;
      color: #6b7280;
      max-width: 55%;
      line-height: 1.4;
    }
    .tabla-totales {
      width: 250px;
      font-size: 8.5pt;
      font-family: monospace;
      border-collapse: collapse;
    }
    .tabla-totales td {
      padding: 2.5px 0;
    }
    .total-destacado {
      font-size: 10.5pt;
      font-weight: 800;
      border-top: 1px solid #374151;
      padding-top: 5px !important;
      color: #111827;
    }
    .badge-cancelado {
      display: inline-block;
      margin-top: 6px;
      padding: 3px 8px;
      border-radius: 9999px;
      background: #ecfdf5;
      color: #065f46;
      font-weight: 700;
      font-size: 7.5pt;
      border: 1px solid #a7f3d0;
    }
    .badge-pendiente {
      display: inline-block;
      margin-top: 6px;
      padding: 3px 8px;
      border-radius: 9999px;
      background: #fffbeb;
      color: #92400e;
      font-weight: 700;
      font-size: 7.5pt;
      border: 1px solid #fde68a;
    }
  </style>
</head>
<body>
  <div class="comprobante-wrapper">
    <div class="header-row">
      <div>
        <div class="empresa-titulo">${params.nombreEmpresa}</div>
        <div class="empresa-rubro">${params.rubroEmpresa}</div>
        <div class="empresa-datos">
          ${params.nombreSede}<br/>
          ${params.direccionSede ? params.direccionSede + '<br/>' : ''}
          ${params.telefonoSede ? 'Tel/WhatsApp: +51 ' + params.telefonoSede : ''}
        </div>
      </div>
      <div class="box-comprobante">
        <div class="box-tipo">COMPROBANTE DE PAGO</div>
        <div class="box-ruc">Documento interno · sin valor tributario</div>
        <div class="box-serie">${params.serieCorrelativo}</div>
      </div>
    </div>

    <div class="datos-cliente">
      <div>
        <div><strong>Señor(es):</strong> ${params.razonSocial}</div>
        <div><strong>${params.tipoDocLabel}:</strong> ${params.numeroDoc}</div>
        <div><strong>Dirección:</strong> ${params.direccionCliente}</div>
        <div><strong>${params.conceptoLabel}:</strong> ${params.conceptoNombre}</div>
      </div>
      <div class="text-right">
        <div><strong>Fecha de Emisión:</strong> ${params.fechaEmision}</div>
        <div><strong>Moneda:</strong> Soles (PEN - S/)</div>
        <div><strong>${params.etiquetaDoctor}</strong> ${params.doctorNombre}</div>
      </div>
    </div>

    <table class="tabla-items">
      <thead>
        <tr>
          <th>Descripción del Procedimiento</th>
          <th class="text-center" style="width: 70px;">Pieza FDI</th>
          <th class="text-center" style="width: 50px;">Cant.</th>
          <th class="text-right" style="width: 90px;">P. Unitario</th>
          <th class="text-right" style="width: 90px;">Importe</th>
        </tr>
      </thead>
      <tbody>
        ${params.items.map((it) => `
          <tr>
            <td>${it.nombre}</td>
            <td class="text-center font-mono">${it.pieza}</td>
            <td class="text-center">${it.cantidad}</td>
            <td class="text-right font-mono">${formatSoles(it.precioUnitario)}</td>
            <td class="text-right font-mono" style="font-weight: 600;">${formatSoles(it.importe)}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>

    <div class="totales-row">
      <div class="condiciones">
        <div>Modalidad de Pago: <strong>${params.modalidadPago}</strong></div>
        ${params.mediosDePago ? `<div>Medio de pago: <strong>${params.mediosDePago}</strong></div>` : ''}
        <div style="font-style: italic; margin-top: 3px;">Precios incluyen I.G.V. (18%) conforme a la normativa comercial y tributaria.</div>
        ${!params.faltaPagar ? `
          <div class="badge-cancelado">✓ CANCELADO EN TOTALIDAD</div>
        ` : `
          <div class="badge-pendiente">⚠️ ${params.mensajeBloqueo}</div>
        `}
      </div>

      <table class="tabla-totales">
        ${params.desglosarIgv ? `
          <tr>
            <td style="color: #4b5563;">Subtotal (Op. Gravada):</td>
            <td class="text-right">${formatSoles(params.subtotalGravado)}</td>
          </tr>
          ${params.descuento > 0 ? `
            <tr style="color: #047857;">
              <td>Descuento:</td>
              <td class="text-right">-${formatSoles(params.descuento)}</td>
            </tr>
          ` : ''}
          <tr>
            <td style="color: #4b5563;">I.G.V. (18%):</td>
            <td class="text-right">${formatSoles(params.igvCalculado)}</td>
          </tr>
        ` : `
          <tr>
            <td style="color: #4b5563;">Subtotal:</td>
            <td class="text-right">${formatSoles(params.subtotal)}</td>
          </tr>
          ${params.descuento > 0 ? `
            <tr style="color: #047857;">
              <td>Descuento:</td>
              <td class="text-right">-${formatSoles(params.descuento)}</td>
            </tr>
          ` : ''}
        `}
        <tr class="total-destacado">
          <td>TOTAL:</td>
          <td class="text-right">${formatSoles(params.total)}</td>
        </tr>
        <tr>
          <td style="color: #374151;">Monto Pagado:</td>
          <td class="text-right">${formatSoles(params.pagado)}</td>
        </tr>
        <tr>
          <td style="color: #374151;">Saldo Restante:</td>
          <td class="text-right ${params.faltaPagar ? 'style="color: #92400e; font-weight: bold;"' : ''}">${formatSoles(params.saldoPendiente)}</td>
        </tr>
      </table>
    </div>
  </div>
</body>
</html>`
}

export function ImprimirComprobanteModal({
  abierto,
  alCerrar,
  tratamiento,
  pacienteNombre,
  pacienteDni,
  sedeNombre,
  sedeDireccion,
  sedeTelefono,
  negocio: negocioProp,
  comprobantes = [],
  mediosDePago = [],
}: Props) {
  const sesion = useSesion()
  const negocioActivo = negocioProp ?? sesion?.negocioActivo ?? 'VILLADENT'
  const esVillamax = negocioActivo === 'VILLAMAX'

  const nombreEmpresa = esVillamax ? 'VILLAMAX 3D' : 'VILLADENT ODONTOLOGÍA'
  const rubroEmpresa = esVillamax ? 'Centro Radiológico Maxilofacial' : 'Red Odontológica Especializada'
  const etiquetaDoctor = esVillamax ? 'Especialista:' : 'Odontólogo:'

  const nombreSedeEfectiva = sedeNombre ?? (esVillamax ? 'Villamax Chilca' : 'Sede Central')
  // La dirección y el teléfono son los de la sede registrada; si faltan se omiten (nunca se inventan).
  const direccionSedeEfectiva = sedeDireccion ?? ''
  const telefonoSedeEfectivo = sedeTelefono ?? ''

  // 'BOLETA' = cliente identificado con DNI; 'FACTURA' = cliente identificado con RUC (solo cambia el dato del cliente).
  const [tipoComprobante, setTipoComprobante] = useState<'BOLETA' | 'FACTURA'>('BOLETA')
  const [serieCorrelativo, setSerieCorrelativo] = useState(() => comprobantes.join(', '))
  const [razonSocial, setRazonSocial] = useState(pacienteNombre || '')
  const [numeroDoc, setNumeroDoc] = useState(pacienteDni || '')
  const [direccionCliente, setDireccionCliente] = useState('Huancayo, Junín')
  const [desglosarIgv, setDesglosarIgv] = useState(true)

  if (!tratamiento) return null

  const items = tratamiento.items ?? []
  const subtotal = tratamiento.costoTotal ?? 0
  const descuento = tratamiento.descuentoTotal ?? 0
  // En Odontomax / Villadent los precios de tratamientos/planes YA INCLUYEN IGV.
  // El total del comprobante debe ser exactamente el monto del plan (Total = costoTotal - descuentoTotal).
  const total = Math.max(0, subtotal - descuento)
  // Desglose informativo SUNAT:
  // Subtotal (Operación Gravada) = Total / 1.18 (redondeado a 2 decimales)
  // IGV (18%) = Total - Subtotal. La suma da exactamente el Total.
  const subtotalGravado = Math.round((total / 1.18) * 100) / 100
  const igvCalculado = Math.round((total - subtotalGravado) * 100) / 100

  // Saldo restante del plan
  const saldoPendiente = Math.max(0, Number(tratamiento.saldoPendiente ?? 0))
  const montoPagadoReal = Number(tratamiento.montoPagado ?? 0)
  const pagado = saldoPendiente === 0 ? total : Math.min(total, montoPagadoReal)
  const faltaPagar = saldoPendiente > 0
  const mensajeBloqueo = `Falta pagar ${formatSoles(saldoPendiente)} para emitir el comprobante`

  const handleCambiarTipo = (tipo: 'BOLETA' | 'FACTURA') => {
    setTipoComprobante(tipo)
    setNumeroDoc('')
  }

  const handleImprimir = () => {
    if (faltaPagar) return
    const params: HtmlComprobanteParams = {
      nombreEmpresa,
      rubroEmpresa,
      nombreSede: nombreSedeEfectiva,
      direccionSede: direccionSedeEfectiva,
      telefonoSede: telefonoSedeEfectivo,
      tipoComprobante,
      serieCorrelativo,
      razonSocial: razonSocial || 'Público General',
      numeroDoc: numeroDoc || '-',
      tipoDocLabel: tipoComprobante === 'FACTURA' ? 'RUC' : 'DNI/Doc',
      direccionCliente,
      conceptoNombre: tratamiento.nombre ?? '',
      conceptoLabel: esVillamax ? 'Estudio / Servicio' : 'Tratamiento',
      fechaEmision: formatFecha(new Date().toISOString()),
      doctorNombre: tratamiento.especialistaNombre ?? (esVillamax ? 'Especialista en Diagnóstico 3D' : 'Especialista a cargo'),
      etiquetaDoctor,
      items: items.length === 0 ? [{
        nombre: tratamiento.nombre ?? '',
        pieza: '-',
        cantidad: 1,
        precioUnitario: subtotal,
        importe: subtotal,
      }] : items.map((it) => ({
        nombre: it.nombreProcedimiento ?? '',
        pieza: String(it.piezaFdi ?? '-'),
        cantidad: it.cantidad ?? 1,
        precioUnitario: Number(it.precioBase) || 0,
        importe: (Number(it.precioBase) || 0) * (it.cantidad ?? 1) - (Number(it.descuento) || 0),
      })),
      modalidadPago: tratamiento.modalidadPago ?? '',
      mediosDePago: mediosDePago.join(', '),
      faltaPagar,
      mensajeBloqueo,
      desglosarIgv,
      subtotal,
      subtotalGravado,
      descuento,
      igvCalculado,
      total,
      pagado,
      saldoPendiente,
    }

    const html = generarHtmlComprobante(params)
    imprimirDocumento(html).catch(() => {
      window.print()
    })
  }

  const handleEnviarWhatsapp = () => {
    if (faltaPagar) return
    const prefijoNegocio = esVillamax ? 'VILLAMAX 3D - CENTRO RADIOLÓGICO' : 'VILLADENT ODONTOLOGÍA'
    const texto = encodeURIComponent(
      `🧾 *COMPROBANTE DE ATENCIÓN - ${prefijoNegocio}*\n` +
      `Estimado(a) ${razonSocial}:\n` +
      `Se ha generado su comprobante de pago *${serieCorrelativo || 'interno'}*.\n\n` +
      `📋 *${esVillamax ? 'Estudio' : 'Tratamiento'}:* ${tratamiento.nombre}\n` +
      `👨‍⚕️ *${etiquetaDoctor}* ${tratamiento.especialistaNombre ?? (esVillamax ? 'Especialista 3D' : 'Especialista')}\n` +
      `💰 *Total:* S/ ${total.toFixed(2)}\n` +
      (desglosarIgv ? `   • Op. Gravada: S/ ${subtotalGravado.toFixed(2)}\n   • IGV (18%): S/ ${igvCalculado.toFixed(2)}\n` : '') +
      `💳 *Monto Pagado:* S/ ${pagado.toFixed(2)}\n` +
      (faltaPagar ? `⚠️ *Saldo Restante:* S/ ${saldoPendiente.toFixed(2)}\n` : `✅ *Condición:* CANCELADO EN TOTALIDAD (Saldo: S/ 0.00)\n`) +
      `📍 *Sede:* ${nombreSedeEfectiva}\n\n` +
      `¡Muchas gracias por su confianza en ${esVillamax ? 'Villamax 3D' : 'Villadent'}!`
    )
    window.open(`https://wa.me/51${telefonoSedeEfectivo}?text=${texto}`, '_blank')
  }

  return (
    <Dialog open={abierto} onOpenChange={(val) => !val && alCerrar()}>
      <DialogContent className="max-w-2xl max-h-[92vh] overflow-y-auto print:max-w-none print:max-h-none print:overflow-visible print:p-0 print:border-none print:shadow-none">
        <DialogHeader className="print:hidden no-print">
          <div className="flex items-center justify-between gap-2">
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-on-surface">
              <FileText className="size-5 text-primary" />
              Comprobante de pago
            </DialogTitle>
          </div>
          <DialogDescription>
            Documento interno para entregar al paciente que lo pida. Identifique al cliente con su DNI o su RUC.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 pt-1 print:space-y-0 print:pt-0">
          {faltaPagar && (
            <Alert tono="aviso" titulo={`Emisión bloqueada: ${mensajeBloqueo}`} className="text-xs print:hidden no-print">
              El comprobante de pago solo se entrega cuando el tratamiento está pagado en su totalidad.
            </Alert>
          )}

          {/* Selector de Tipo de Comprobante */}
          <div className="flex flex-wrap items-center justify-between gap-3 p-3 rounded-xl border border-outline-variant/60 bg-surface-container-low print:hidden no-print">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-on-surface">Cliente identificado con:</span>
              <button
                type="button"
                onClick={() => handleCambiarTipo('BOLETA')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  tipoComprobante === 'BOLETA'
                    ? 'bg-primary text-on-primary shadow-xs'
                    : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface border border-outline-variant'
                }`}
              >
                DNI
              </button>
              <button
                type="button"
                onClick={() => handleCambiarTipo('FACTURA')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  tipoComprobante === 'FACTURA'
                    ? 'bg-primary text-on-primary shadow-xs'
                    : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface border border-outline-variant'
                }`}
              >
                RUC
              </button>
            </div>

            <div className="flex items-center gap-2">
              <label className="flex items-center gap-1.5 text-xs text-on-surface cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={desglosarIgv}
                  onChange={(e) => setDesglosarIgv(e.target.checked)}
                  className="rounded border-outline text-primary focus:ring-primary size-3.5"
                />
                <span>Desglosar IGV (18%)</span>
              </label>
            </div>
          </div>

          {/* Datos del Cliente / Receptor */}
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 p-3 rounded-xl border border-outline-variant/40 bg-surface-container-lowest text-xs print:hidden no-print">
            <Field label={tipoComprobante === 'FACTURA' ? 'Razón Social *' : 'Cliente / Paciente *'}>
              <Input
                value={razonSocial}
                onChange={(e) => setRazonSocial(e.target.value)}
                placeholder="Nombre completo o Razón Social"
              />
            </Field>

            <Field label={tipoComprobante === 'FACTURA' ? 'RUC *' : 'DNI / Carnet *'}>
              <Input
                value={numeroDoc}
                onChange={(e) => setNumeroDoc(e.target.value)}
                placeholder="8 u 11 dígitos"
              />
            </Field>

            <Field label="Dirección Fiscal">
              <Input
                value={direccionCliente}
                onChange={(e) => setDireccionCliente(e.target.value)}
                placeholder="Dirección fiscal o domicilio"
              />
            </Field>

            <Field label="Recibo(s) de caja">
              <Input
                value={serieCorrelativo}
                onChange={(e) => setSerieCorrelativo(e.target.value)}
                className="font-mono font-bold"
              />
            </Field>
          </div>

          {/* VISTA PREVIA DEL COMPROBANTE LISTO PARA IMPRIMIR */}
          <div
            id="area-comprobante-impresion"
            className="p-6 rounded-xl border border-outline-variant/80 bg-white text-neutral-900 shadow-sm font-sans space-y-4 print:p-0 print:border-none print:shadow-none print:w-full print:max-w-none"
          >
            {/* Cabecera Clínica / Membrete */}
            <div data-testid="comprobante-membrete" className="flex flex-col sm:flex-row justify-between items-start gap-4 pb-4 border-b border-neutral-200">
              <div>
                <h3 className="font-display text-lg font-black tracking-tight text-teal-900">
                  {nombreEmpresa}
                </h3>
                <p className="text-xs text-neutral-600 font-semibold">{rubroEmpresa}</p>
                <p className="text-xs text-neutral-500 mt-1">{nombreSedeEfectiva}</p>
                {direccionSedeEfectiva && <p className="text-xs text-neutral-500">{direccionSedeEfectiva}</p>}
                {telefonoSedeEfectivo && <p className="text-xs text-neutral-500">Tel/WhatsApp: +51 {telefonoSedeEfectivo}</p>}
              </div>

              <div className="p-3 border-2 border-neutral-800 rounded-lg text-center min-w-[200px] bg-neutral-50">
                <span className="block text-xs font-bold text-neutral-700">Documento interno · sin valor tributario</span>
                <span className="block text-sm font-black uppercase text-teal-950 py-1">
                  COMPROBANTE DE PAGO
                </span>
                <span className="block text-xs font-mono font-bold text-neutral-800">{serieCorrelativo}</span>
              </div>
            </div>

            {/* Datos del Paciente / Emisión */}
            <div data-testid="comprobante-cliente" className="grid grid-cols-2 gap-2 text-xs text-neutral-700 py-1">
              <div>
                <p><strong>Señor(es):</strong> {razonSocial || 'Público General'}</p>
                <p><strong>{tipoComprobante === 'FACTURA' ? 'RUC' : 'DNI/Doc'}:</strong> {numeroDoc || '-'}</p>
                <p><strong>Dirección:</strong> {direccionCliente}</p>
                <p><strong>{esVillamax ? 'Estudio / Servicio:' : 'Tratamiento:'}</strong> {tratamiento.nombre}</p>
              </div>
              <div className="text-right">
                <p><strong>Fecha de Emisión:</strong> {formatFecha(new Date().toISOString())}</p>
                <p><strong>Moneda:</strong> Soles (PEN - S/)</p>
                <p><strong>{etiquetaDoctor}</strong> {tratamiento.especialistaNombre ?? (esVillamax ? 'Especialista en Diagnóstico 3D' : 'Especialista a cargo')}</p>
              </div>
            </div>

            {/* Tabla de Procedimientos */}
            <div data-testid="comprobante-detalle" className="border border-neutral-200 rounded overflow-hidden">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-neutral-100 text-neutral-700 border-b border-neutral-200 font-semibold">
                  <tr>
                    <th className="py-2 px-3">Descripción del Procedimiento</th>
                    <th className="py-2 px-2 text-center">Pieza FDI</th>
                    <th className="py-2 px-2 text-center">Cant.</th>
                    <th className="py-2 px-3 text-right">P. Unitario</th>
                    <th className="py-2 px-3 text-right">Importe</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {items.length === 0 ? (
                    <tr>
                      <td className="py-2 px-3">{tratamiento.nombre}</td>
                      <td className="py-2 px-2 text-center">-</td>
                      <td className="py-2 px-2 text-center">1</td>
                      <td className="py-2 px-3 text-right">{formatSoles(subtotal)}</td>
                      <td className="py-2 px-3 text-right font-bold">{formatSoles(subtotal)}</td>
                    </tr>
                  ) : (
                    items.map((it, idx) => (
                      <tr key={it.id ?? idx}>
                        <td className="py-2 px-3 font-medium">{it.nombreProcedimiento}</td>
                        <td className="py-2 px-2 text-center font-mono">{it.piezaFdi ?? '-'}</td>
                        <td className="py-2 px-2 text-center">{it.cantidad ?? 1}</td>
                        <td className="py-2 px-3 text-right font-mono">{formatSoles(it.precioBase)}</td>
                        <td className="py-2 px-3 text-right font-mono font-bold">
                          {formatSoles((Number(it.precioBase) || 0) * (it.cantidad ?? 1) - (Number(it.descuento) || 0))}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {/* Resumen de Totales */}
            <div data-testid="comprobante-totales" className="flex flex-col sm:flex-row justify-between items-start gap-4 pt-2">
              <div className="text-xs text-neutral-500 max-w-xs space-y-1">
                <p>Modalidad de Pago: <strong>{tratamiento.modalidadPago}</strong></p>
                {mediosDePago.length > 0 && <p>Medio de pago: <strong>{mediosDePago.join(', ')}</strong></p>}
                <p className="italic">
                  Precios incluyen I.G.V. (18%) conforme a la normativa comercial y tributaria.
                </p>
                {!faltaPagar ? (
                  <span className="inline-flex items-center gap-1 text-emerald-700 font-bold bg-emerald-50 px-2.5 py-1 rounded-full text-xs border border-emerald-200 mt-1">
                    <CheckCircle2 className="size-3.5" /> CANCELADO EN TOTALIDAD
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-amber-800 font-bold bg-amber-50 px-2.5 py-1 rounded-full text-xs border border-amber-200 mt-1">
                    {mensajeBloqueo}
                  </span>
                )}
              </div>

              <div className="w-full sm:w-64 text-xs font-mono space-y-1.5 border-t sm:border-t-0 pt-2 sm:pt-0">
                {desglosarIgv ? (
                  <>
                    <div className="flex justify-between">
                      <span className="text-neutral-600">Subtotal (Op. Gravada):</span>
                      <span>{formatSoles(subtotalGravado)}</span>
                    </div>
                    {descuento > 0 && (
                      <div className="flex justify-between text-emerald-700">
                        <span>Descuento:</span>
                        <span>-{formatSoles(descuento)}</span>
                      </div>
                    )}
                    <div className="flex justify-between text-neutral-600">
                      <span>I.G.V. (18%):</span>
                      <span>{formatSoles(igvCalculado)}</span>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="flex justify-between">
                      <span className="text-neutral-600">Subtotal:</span>
                      <span>{formatSoles(subtotal)}</span>
                    </div>
                    {descuento > 0 && (
                      <div className="flex justify-between text-emerald-700">
                        <span>Descuento:</span>
                        <span>-{formatSoles(descuento)}</span>
                      </div>
                    )}
                  </>
                )}
                <div className="flex justify-between text-sm font-bold border-t border-neutral-300 pt-1 text-neutral-900">
                  <span>TOTAL:</span>
                  <span>{formatSoles(total)}</span>
                </div>
                <div className="flex justify-between text-neutral-700">
                  <span>Monto Pagado:</span>
                  <span>{formatSoles(pagado)}</span>
                </div>
                <div className="flex justify-between text-neutral-700">
                  <span>Saldo Restante:</span>
                  <span className={faltaPagar ? 'text-amber-800 font-bold' : ''}>{formatSoles(saldoPendiente)}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Botones de Acción */}
          <div className="flex flex-wrap items-center justify-end gap-2.5 pt-3 border-t border-outline-variant/60 print:hidden no-print">
            <Button type="button" variant="secundario" onClick={alCerrar}>
              Cerrar
            </Button>
            <Button
              type="button"
              variant="secundario"
              onClick={handleEnviarWhatsapp}
              disabled={faltaPagar}
              title={faltaPagar ? mensajeBloqueo : 'Enviar resumen por WhatsApp'}
              className="gap-1.5 text-xs text-emerald-700 hover:text-emerald-800 border-emerald-200 bg-emerald-50/60"
            >
              <MessageCircle className="size-4" />
              Enviar Resumen WhatsApp
            </Button>
            <Button
              type="button"
              variant="accion"
              onClick={handleImprimir}
              disabled={faltaPagar}
              title={faltaPagar ? mensajeBloqueo : 'Imprimir Comprobante'}
              className="gap-1.5 text-xs"
            >
              <Printer className="size-4" />
              Imprimir Comprobante
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
