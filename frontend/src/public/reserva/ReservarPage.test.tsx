import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { hoyEnLima } from '@/shared/format'
import { http } from 'msw'
import { API, HttpResponse, errorBackend, limiteReserva, manejadores, respuestas, servidor, ticketReserva } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import { _olvidarTickets, leerTicket } from './ticket-local'

beforeEach(() => {
  sessionStorage.clear()
  _olvidarTickets()
  servidor.use(manejadores.sedes(), manejadores.catalogoServicios(), manejadores.catalogoEspecialistas())
})

async function completarPaso1() {
  await userEvent.click(await screen.findByRole('radio', { name: /Sede Chilca/ }))
  await userEvent.click(screen.getByRole('radio', { name: /Evaluación & Diagnóstico/ }))
  await userEvent.click(screen.getByRole('button', { name: /Continuar a selección de fecha/ }))
  await screen.findByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })
}

async function completarPaso2() {
  await userEvent.click(screen.getByRole('radio', { name: /\(hoy\)/ }))
  await userEvent.click(screen.getByRole('radio', { name: 'Mañana' }))
  await userEvent.click(screen.getByRole('button', { name: /Continuar a tus datos/ }))
  await screen.findByRole('heading', { name: 'Paso 3 de 4: Tus datos' })
}

async function completarPaso3() {
  await userEvent.type(screen.getByLabelText('DNI o documento'), '45908123')
  await userEvent.type(screen.getByLabelText('Nombres'), '  Carlos ')
  await userEvent.type(screen.getByLabelText('Apellidos'), 'Huamán Rojas')
  await userEvent.type(screen.getByLabelText('Celular o WhatsApp'), '987654321')
  await userEvent.click(screen.getByRole('button', { name: /Revisar mi reserva/ }))
  await screen.findByRole('heading', { name: 'Paso 4 de 4: Confirmar' })
}

describe('A4 · Asistente de reserva', { timeout: 30_000 }, () => {
  it('carga sedes y servicios reales (solo activos, por orden) y valida el paso 1', async () => {
    await renderRuta('/reservar')
    expect(screen.getByText('Paso 1 de 4')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Pasos de la reserva' })).toBeInTheDocument()

    const servicios = await screen.findAllByRole('radio', { name: /Evaluación|Limpieza|retirado/ })
    expect(servicios.map((r) => r.closest('label')?.textContent)).toEqual([
      expect.stringContaining('Evaluación & Diagnóstico'),
      expect.stringContaining('Limpieza & Profilaxis profunda'),
    ])
    expect(screen.queryByText('S/ 30.00')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Continuar a selección de fecha/ }))
    expect(await screen.findByText('Selecciona una sede.')).toBeInTheDocument()
    expect(screen.getByText('Selecciona un servicio.')).toBeInTheDocument()
    expect(screen.getByText('Paso 1 de 4')).toBeInTheDocument()
  })

  it('recorre los 4 pasos, valida cada uno y envía el cuerpo exacto del contrato', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.reservar((c) => {
        cuerpo = c
        return respuestas.ticket(ticketReserva({ fecha: hoyEnLima() }))
      }),
    )
    const { router } = await renderRuta('/reservar')
    await completarPaso1()
    // El foco va al título del paso para que el lector lo anuncie.
    expect(screen.getByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })).toHaveFocus()

    // Paso 2: fecha y turno son obligatorios.
    await userEvent.click(screen.getByRole('button', { name: /Continuar a tus datos/ }))
    expect(await screen.findByText('Elige una fecha.')).toBeInTheDocument()
    expect(screen.getByText('Elige el turno de mañana o de tarde.')).toBeInTheDocument()
    await completarPaso2()

    // Paso 3: mismas validaciones que ReservaPublicaRequestDTO.
    await userEvent.type(screen.getByLabelText('DNI o documento'), '1234')
    await userEvent.type(screen.getByLabelText('Celular o WhatsApp'), '98 765')
    await userEvent.click(screen.getByRole('button', { name: /Revisar mi reserva/ }))
    expect(await screen.findByText('El DNI o documento debe contener entre 8 y 12 dígitos.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tus nombres.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tus apellidos.')).toBeInTheDocument()
    expect(screen.getByText(/Ingresa un número de teléfono o WhatsApp válido/)).toBeInTheDocument()
    expect(screen.getByLabelText('DNI o documento')).toHaveFocus()
    await userEvent.clear(screen.getByLabelText('DNI o documento'))
    await userEvent.clear(screen.getByLabelText('Celular o WhatsApp'))
    await completarPaso3()

    // Paso 4: revisión con todo lo elegido y aviso de pago en clínica (no hay adelanto en la API).
    const revision = screen.getByRole('region', { name: 'Revisa tu reserva' })
    expect(within(revision).getByText('Sede Chilca')).toBeInTheDocument()
    expect(within(revision).getByText(/Sin preferencia/)).toBeInTheDocument()
    expect(within(revision).getByText('Evaluación & Diagnóstico')).toBeInTheDocument()
    expect(within(revision).getByText('Mañana')).toBeInTheDocument()
    expect(within(revision).getByText(/Documento: 45908123/)).toBeInTheDocument()
    expect(screen.getByText('No necesitas pagar ahora')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/reserva/OM-7K9M2P4X'))
    expect(cuerpo).toEqual({
      dni: '45908123',
      nombres: 'Carlos',
      apellidos: 'Huamán Rojas',
      telefono: '987654321',
      motivoConsulta: 'Evaluación & Diagnóstico',
      sedeId: 'sede-chilca',
      fechaPreferencia: hoyEnLima(),
      turno: 'MANANA',
    })
    expect(leerTicket('OM-7K9M2P4X')?.codigoTicket).toBe('OM-7K9M2P4X')
  })

  it('"Cambiar" en la revisión vuelve al paso correspondiente', async () => {
    await renderRuta('/reservar')
    await completarPaso1()
    await completarPaso2()
    await completarPaso3()
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar turno' }))
    expect(await screen.findByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })).toHaveFocus()
    expect(screen.getByRole('radio', { name: 'Mañana' })).toBeChecked()
  })

  it('429: muestra el mensaje del backend y no avanza', async () => {
    const mensaje = 'Ha superado el límite de intentos de reserva. Por favor espere unos minutos o contáctenos por WhatsApp.'
    servidor.use(manejadores.reservar(() => limiteReserva(mensaje)))
    const { router } = await renderRuta('/reservar')
    await completarPaso1()
    await completarPaso2()
    await completarPaso3()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Demasiados intentos de reserva')
    expect(alerta).toHaveTextContent(mensaje)
    expect(router.state.location.pathname).toBe('/reservar')
  })

  it('400 (sin cupo en el turno): muestra el mensaje y ofrece elegir otra fecha', async () => {
    const mensaje =
      'No hay disponibilidad para nuevas reservas web en el turno seleccionado. Por favor seleccione otra fecha o turno.'
    servidor.use(manejadores.reservar(() => errorBackend(400, mensaje)))
    await renderRuta('/reservar')
    await completarPaso1()
    await completarPaso2()
    await completarPaso3()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    expect(await screen.findByText(mensaje)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Elegir otra fecha o turno' }))
    expect(await screen.findByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })).toBeInTheDocument()
  })

  it('error al cargar las sedes: estado de error con reintento', async () => {
    let fallar = true
    // MSW consulta primero el último manejador registrado: falla una vez y después cae en manejadores.sedes().
    servidor.use(
      http.get(`${API}/sedes`, () => {
        if (!fallar) return undefined
        fallar = false
        return new HttpResponse(null, { status: 500 })
      }),
    )
    await renderRuta('/reservar')
    expect(await screen.findByText('No pudimos cargar las sedes')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('radio', { name: /Sede Chilca/ })).toBeInTheDocument()
  })

  it('sin sedes activas: estado vacío', async () => {
    servidor.use(manejadores.sedes([]))
    await renderRuta('/reservar')
    expect(await screen.findByText('No hay sedes disponibles')).toBeInTheDocument()
  })

  it('permite elegir entre Villadent y Villamax, filtrando sedes y estudios sin precios', async () => {
    await renderRuta('/reservar')

    // Por defecto es Villadent
    expect(screen.getByRole('radio', { name: /Odontología \(Villadent\)/ })).toBeChecked()
    expect(screen.getByRole('heading', { level: 1, name: 'Reserva tu cita' })).toBeInTheDocument()
    expect(await screen.findByRole('radio', { name: /Sede Chilca/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Villamax Chilca/ })).not.toBeInTheDocument()
    expect(await screen.findByRole('radio', { name: /Evaluación & Diagnóstico/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Radiografía Panorámica/ })).not.toBeInTheDocument()

    // Cambiar a Villamax
    await userEvent.click(screen.getByRole('radio', { name: /Radiología 3D \(Villamax\)/ }))

    // El encabezado cambia
    expect(await screen.findByRole('heading', { level: 1, name: 'Reserva tu estudio radiológico' })).toBeInTheDocument()
    expect(screen.getByText('Villamax 3D · Centro Radiológico')).toBeInTheDocument()

    // Sedes y servicios filtrados a Villamax (sin mostrar precios)
    expect(await screen.findByRole('radio', { name: /Villamax Chilca/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Sede Chilca \(Local Chilca\)/ })).not.toBeInTheDocument()
    expect(await screen.findByRole('radio', { name: /Radiografía Panorámica/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Tomografía Cone Beam 3D/ })).toBeInTheDocument()
    expect(screen.queryByText('S/ 60.00')).not.toBeInTheDocument()
    expect(screen.queryByText('S/ 150.00')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Evaluación & Diagnóstico/ })).not.toBeInTheDocument()
  })

  it('cambiar de negocio reinicia la selección de sede y servicio', async () => {
    await renderRuta('/reservar')

    const radioSede = await screen.findByRole('radio', { name: /Sede Chilca/ })
    await userEvent.click(radioSede)
    expect(radioSede).toBeChecked()

    const radioServicio = screen.getByRole('radio', { name: /Evaluación & Diagnóstico/ })
    await userEvent.click(radioServicio)
    expect(radioServicio).toBeChecked()

    // Cambiar a Villamax reinicia selección
    await userEvent.click(screen.getByRole('radio', { name: /Radiología 3D \(Villamax\)/ }))

    // Los radios de Villadent ya no están y los de Villamax inician desmarcados
    const radioVillamax = await screen.findByRole('radio', { name: /Villamax Chilca/ })
    expect(radioVillamax).not.toBeChecked()
    const radioEstudio = screen.getByRole('radio', { name: /Radiografía Panorámica/ })
    expect(radioEstudio).not.toBeChecked()
  })

  it('doctor por sede: Chilca muestra 4 doctores, Huancayo Centro muestra 2 y Villamax muestra 1', async () => {
    await renderRuta('/reservar')

    // Al elegir Sede Chilca: muestra sus 4 doctores oficiales (Doctor A-D)
    await userEvent.click(await screen.findByRole('radio', { name: /Sede Chilca/ }))
    expect(await screen.findByRole('radio', { name: /Doctor A/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Doctor B/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Doctor C/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Doctor D/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Doctor E/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Doctor F/ })).not.toBeInTheDocument()

    // Al elegir Sede Huancayo Centro: muestra sus 2 doctores oficiales (Doctor E-F)
    await userEvent.click(screen.getByRole('radio', { name: /Sede Huancayo Centro/ }))
    expect(await screen.findByRole('radio', { name: /Doctor E/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Doctor F/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Doctor A/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Doctor B/ })).not.toBeInTheDocument()

    // Al cambiar a Villamax: muestra su único doctor
    await userEvent.click(screen.getByRole('radio', { name: /Radiología 3D \(Villamax\)/ }))
    await userEvent.click(await screen.findByRole('radio', { name: /Villamax Chilca/ }))
    expect(await screen.findByRole('radio', { name: /Doctor Villamax/ })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /Doctor A/ })).not.toBeInTheDocument()
  })

  it('casilla "Soy paciente nuevo" y selección de doctor preferido', async () => {
    let cuerpoEnviado: Record<string, unknown> | undefined
    servidor.use(
      manejadores.reservar((c) => {
        cuerpoEnviado = c as Record<string, unknown>
        return respuestas.ticket(ticketReserva({ fecha: hoyEnLima() }))
      }),
    )

    await renderRuta('/reservar')
    // Paso 1: elegir sede, doctor específico y servicio
    await userEvent.click(await screen.findByRole('radio', { name: /Sede Chilca/ }))
    await userEvent.click(await screen.findByRole('radio', { name: /Doctor B/ }))
    await userEvent.click(screen.getByRole('radio', { name: /Evaluación & Diagnóstico/ }))
    await userEvent.click(screen.getByRole('button', { name: /Continuar a selección de fecha/ }))

    // Paso 2: fecha y turno
    await screen.findByRole('heading', { name: 'Paso 2 de 4: Fecha y turno' })
    await userEvent.click(screen.getByRole('radio', { name: /\(hoy\)/ }))
    await userEvent.click(screen.getByRole('radio', { name: 'Mañana' }))
    await userEvent.click(screen.getByRole('button', { name: /Continuar a tus datos/ }))

    // Paso 3: marcar "Soy paciente nuevo"
    await screen.findByRole('heading', { name: 'Paso 3 de 4: Tus datos' })
    await userEvent.click(screen.getByLabelText(/Soy paciente nuevo/i))
    await userEvent.type(screen.getByLabelText('DNI o documento'), '71234567')
    await userEvent.type(screen.getByLabelText('Nombres'), 'Mariana')
    await userEvent.type(screen.getByLabelText('Apellidos'), 'Gómez')
    await userEvent.type(screen.getByLabelText('Celular o WhatsApp'), '987111222')
    await userEvent.click(screen.getByRole('button', { name: /Revisar mi reserva/ }))

    // Paso 4: confirmar
    await screen.findByRole('heading', { name: 'Paso 4 de 4: Confirmar' })
    expect(screen.getAllByText('Doctor B')[0]).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }))
    await waitFor(() => expect(cuerpoEnviado).toBeDefined())
    expect(cuerpoEnviado?.pacienteNuevo).toBe(true)
    expect(cuerpoEnviado?.doctorId).toBe('10000000-0000-0000-0000-000000000002')
  })
})
