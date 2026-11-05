import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ImprimirComprobanteModal } from './ImprimirComprobanteModal'
import * as imprimirModulo from '@/shared/imprimir'
import type { TratamientoDTO } from '../tratamientos/api-tratamientos'

describe('ImprimirComprobanteModal · comprobante de pago interno con impresión completa y soporte multi-negocio', () => {
  const tratamientoPagadoCompleto: TratamientoDTO = {
    id: 'trat-1',
    pacienteId: 'pac-1',
    nombre: 'Tratamiento Integral de Ortodoncia',
    costoTotal: 1500,
    descuentoTotal: 0,
    montoPagado: 1500,
    saldoPendiente: 0,
    estado: 'COMPLETADO',
    modalidadPago: 'AL_CONTADO',
    totalSesiones: 10,
    sesionesRealizadas: 10,
    especialistaNombre: 'Dr. Roberto Carlos',
  }

  const tratamientoConSaldoPendiente: TratamientoDTO = {
    id: 'trat-2',
    pacienteId: 'pac-1',
    nombre: 'Tratamiento Integral de Ortodoncia',
    costoTotal: 1500,
    descuentoTotal: 0,
    montoPagado: 240,
    saldoPendiente: 1260,
    estado: 'EN_PROCESO',
    modalidadPago: 'EN_CUOTAS',
    totalSesiones: 10,
    sesionesRealizadas: 2,
    especialistaNombre: 'Dr. Roberto Carlos',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('con saldo 0 calcula IGV incluido: Total S/ 1,500.00, Subtotal S/ 1,271.19, IGV S/ 228.81 y Saldo S/ 0.00', () => {
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoPagadoCompleto}
        pacienteNombre="Juan Pérez"
        pacienteDni="12345678"
      />
    )

    // El Total debe ser exactamente S/ 1,500.00 (el precio YA incluye IGV, NUNCA sumar 18% encima)
    expect(screen.getByText('TOTAL:')).toBeInTheDocument()
    expect(screen.getAllByText('S/ 1,500.00').length).toBeGreaterThanOrEqual(1)

    // Desglose informativo: Subtotal (Op. Gravada) = 1500 / 1.18 = 1271.19
    expect(screen.getByText('Subtotal (Op. Gravada):')).toBeInTheDocument()
    expect(screen.getByText('S/ 1,271.19')).toBeInTheDocument()

    // IGV (18%) = 1500 - 1271.19 = 228.81
    expect(screen.getByText('I.G.V. (18%):')).toBeInTheDocument()
    expect(screen.getByText('S/ 228.81')).toBeInTheDocument()

    // Monto Pagado y Saldo Restante en 0
    expect(screen.getByText('Monto Pagado:')).toBeInTheDocument()
    expect(screen.getByText('Saldo Restante:')).toBeInTheDocument()
    expect(screen.getByText('S/ 0.00')).toBeInTheDocument()
    expect(screen.getByText(/CANCELADO EN TOTALIDAD/i)).toBeInTheDocument()

    // Botones habilitados para imprimir y enviar
    const botonImprimir = screen.getByRole('button', { name: /Imprimir Comprobante/i })
    expect(botonImprimir).not.toBeDisabled()

    const botonWhatsapp = screen.getByRole('button', { name: /Enviar Resumen WhatsApp/i })
    expect(botonWhatsapp).not.toBeDisabled()
  })

  it('con saldo pendiente bloquea la emisión y muestra alerta explicativa con el monto faltante', () => {
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoConSaldoPendiente}
        pacienteNombre="Juan Pérez"
        pacienteDni="12345678"
      />
    )

    // Alerta explicativa y saldo restante
    expect(screen.getByText(/Emisión bloqueada:/i)).toBeInTheDocument()
    expect(screen.getAllByText(/Falta pagar S\/ 1,260\.00 para emitir el comprobante/i).length).toBeGreaterThanOrEqual(1)

    // Los botones de imprimir y whatsapp deben estar estrictamente deshabilitados
    const botonImprimir = screen.getByRole('button', { name: /Imprimir Comprobante/i })
    expect(botonImprimir).toBeDisabled()
    expect(botonImprimir).toHaveAttribute('title', 'Falta pagar S/ 1,260.00 para emitir el comprobante')

    const botonWhatsapp = screen.getByRole('button', { name: /Enviar Resumen WhatsApp/i })
    expect(botonWhatsapp).toBeDisabled()
  })

  it('identifica al cliente con DNI o con RUC y muestra los recibos reales de caja, sin simular boleta ni factura', async () => {
    const user = userEvent.setup()
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoPagadoCompleto}
        pacienteNombre="Empresa Dental SAC"
        pacienteDni="12345678"
        comprobantes={['R001-000012', 'R001-000019']}
      />
    )

    expect(screen.getByText('COMPROBANTE DE PAGO')).toBeInTheDocument()
    expect(screen.queryByText(/BOLETA DE VENTA|FACTURA ELECTRÓNICA/)).not.toBeInTheDocument()
    expect(screen.getByDisplayValue('R001-000012, R001-000019')).toBeInTheDocument()
    expect(screen.getByLabelText(/DNI \/ Carnet/)).toHaveValue('12345678')

    await user.click(screen.getByRole('button', { name: 'RUC' }))

    expect(screen.getByLabelText(/RUC \*/)).toHaveValue('')
    expect(screen.getByLabelText(/Razón Social/)).toBeInTheDocument()
  })

  it('el contenedor del comprobante contiene todas las secciones requeridas y clases de impresión', () => {
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoPagadoCompleto}
        pacienteNombre="Juan Pérez"
        pacienteDni="12345678"
        negocio="VILLADENT"
      />
    )

    const areaComprobante = document.getElementById('area-comprobante-impresion')
    expect(areaComprobante).toBeInTheDocument()
    expect(areaComprobante?.className).toContain('print:w-full')
    expect(areaComprobante?.className).toContain('print:border-none')

    // Todas las secciones requeridas
    expect(screen.getByTestId('comprobante-membrete')).toBeInTheDocument()
    expect(screen.getByTestId('comprobante-cliente')).toBeInTheDocument()
    expect(screen.getByTestId('comprobante-detalle')).toBeInTheDocument()
    expect(screen.getByTestId('comprobante-totales')).toBeInTheDocument()

    // Membrete Villadent
    expect(screen.getByText('VILLADENT ODONTOLOGÍA')).toBeInTheDocument()
    expect(screen.getByText('Red Odontológica Especializada')).toBeInTheDocument()

    const elementosOcultosEnPrint = document.querySelectorAll('.print\\:hidden, .no-print')
    expect(elementosOcultosEnPrint.length).toBeGreaterThanOrEqual(4)
  })

  it('con negocio Villamax muestra membrete y datos de Villamax 3D', () => {
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={{
          id: 'est-1',
          pacienteId: 'pac-2',
          nombre: 'Tomografía Cone Beam 3D Bimaxilar',
          costoTotal: 250,
          descuentoTotal: 0,
          montoPagado: 250,
          saldoPendiente: 0,
          estado: 'COMPLETADO',
          modalidadPago: 'AL_CONTADO',
          items: [],
        }}
        pacienteNombre="María López"
        pacienteDni="87654321"
        negocio="VILLAMAX"
      />
    )

    // Membrete Villamax
    expect(screen.getByText('VILLAMAX 3D')).toBeInTheDocument()
    expect(screen.getByText('Centro Radiológico Maxilofacial')).toBeInTheDocument()
    expect(screen.getByText(/Villamax Chilca/i)).toBeInTheDocument()
    expect(screen.getByText('Especialista:')).toBeInTheDocument()
    expect(screen.getAllByText('Tomografía Cone Beam 3D Bimaxilar').length).toBeGreaterThanOrEqual(1)
  })

  it('al presionar Imprimir Comprobante delega en imprimirDocumento con HTML autosuficiente', async () => {
    const spyImprimir = vi.spyOn(imprimirModulo, 'imprimirDocumento').mockResolvedValue(undefined)
    const user = userEvent.setup()

    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoPagadoCompleto}
        pacienteNombre="Juan Pérez"
        pacienteDni="12345678"
      />
    )

    const btnImprimir = screen.getByRole('button', { name: /Imprimir Comprobante/i })
    await user.click(btnImprimir)

    expect(spyImprimir).toHaveBeenCalledTimes(1)
    const htmlGenerado = spyImprimir.mock.calls[0][0]
    expect(htmlGenerado).toContain('<!DOCTYPE html>')
    expect(htmlGenerado).toContain('VILLADENT ODONTOLOGÍA')
    expect(htmlGenerado).toContain('COMPROBANTE DE PAGO')
    expect(htmlGenerado).toContain('sin valor tributario')
    expect(htmlGenerado).not.toContain('20608912345')
    // Nunca se imprime una dirección inventada: sin datos de la sede no hay dirección.
    expect(htmlGenerado).not.toContain('Giráldez')
    expect(htmlGenerado).toContain('Juan Pérez')
    expect(htmlGenerado).toContain('S/ 1,500.00')
  })

  it('imprime la dirección y el teléfono reales de la sede', async () => {
    const user = userEvent.setup()
    const spyImprimir = vi.spyOn(imprimirModulo, 'imprimirDocumento').mockResolvedValue(undefined)
    render(
      <ImprimirComprobanteModal
        abierto={true}
        alCerrar={vi.fn()}
        tratamiento={tratamientoPagadoCompleto}
        pacienteNombre="Juan Pérez"
        pacienteDni="12345678"
        sedeNombre="Villadent Chilca"
        sedeDireccion="Parque de los Héroes, Chilca"
        sedeTelefono="943672062"
      />
    )
    await user.click(screen.getByRole('button', { name: /Imprimir Comprobante/i }))
    const html = spyImprimir.mock.calls[0][0]
    expect(html).toContain('Parque de los Héroes, Chilca')
    expect(html).toContain('943672062')
  })
})
