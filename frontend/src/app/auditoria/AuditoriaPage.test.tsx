import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Esquema } from '@/shared/api'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

type Evento = Esquema<'AuditoriaResponseDTO'>

const E_CAJA: Evento = {
  id: 'ev-1',
  fecha: '2026-10-13',
  hora: '18:30:00',
  usuarioId: 'u-rec',
  usuarioNombre: 'Katherin Mendoza',
  rol: 'RECEPCION',
  sedeId: 'sede-chilca',
  sedeNombre: 'sede-chilca',
  modulo: 'CAJA',
  accion: 'CERRAR_CAJA',
  tabla: 'caja_sesiones',
  registroId: 'c-77',
  detalle: 'Caja cerrada con diferencia de S/ 0.00',
  ip: '190.12.1.1',
}
const E_USUARIO: Evento = {
  id: 'ev-2',
  fecha: '2026-10-13',
  hora: '09:15:00',
  usuarioId: 'u-adm',
  usuarioNombre: 'Lucía Paredes',
  rol: 'ADMIN_GENERAL',
  sedeNombre: 'General',
  modulo: 'USUARIOS',
  accion: 'CREAR_USUARIO',
  tabla: 'usuarios',
  registroId: 'u-99',
  detalle: 'Usuario creado: Dr. Mario Rivas',
  ip: '10.0.0.5',
}
const E_HISTORIA: Evento = {
  id: 'ev-3',
  fecha: '2026-10-12',
  hora: '11:00:00',
  usuarioId: 'u-doc',
  usuarioNombre: 'Dra. Elena Paucar',
  rol: 'ODONTOLOGO',
  sedeId: 'sede-centro',
  sedeNombre: 'sede-centro',
  modulo: 'HISTORIA_CLINICA',
  accion: 'APERTURA_HISTORIA',
  tabla: 'historia_clinica_accesos',
  registroId: 'p-1',
  detalle: '{"pacienteId":"p-1","motivo":"consulta"}',
  ip: '181.65.2.9',
}
const BASE = [E_CAJA, E_USUARIO, E_HISTORIA]

function sesion(rol: Rol = 'ADMIN_GENERAL') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Andrea Quispe', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

let consultas: (string | null)[] = []

/** Simula al backend: el único filtro que entiende es ?modulo= (nombre exacto, sin distinguir mayúsculas). */
function api(eventos: Evento[] = BASE) {
  consultas = []
  servidor.use(
    http.get(`${API}/auditoria`, ({ request }) => {
      const modulo = new URL(request.url).searchParams.get('modulo')
      consultas.push(modulo)
      return HttpResponse.json(modulo ? eventos.filter((e) => e.modulo?.toLowerCase() === modulo.toLowerCase()) : eventos)
    }),
  )
}

const filas = () => screen.getAllByRole('row').slice(1)
const fila = (texto: string) => within(screen.getByText(texto).closest('tr') as HTMLElement)

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  sesion()
})
afterEach(() => limpiarSesion())

describe('Módulo 7 · Auditoría del Sistema', { timeout: 20_000 }, () => {
  it('muestra esqueleto y luego la tabla con usuario, rol, sede, módulo, acción, detalle e IP', async () => {
    api()
    await renderRuta('/app/auditoria')
    expect(screen.getByRole('heading', { level: 1, name: 'Auditoría del Sistema' })).toBeInTheDocument()
    expect(screen.getByText('Registro cronológico de operaciones, accesos y cambios críticos')).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Cargando eventos' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Auditoría' })).toHaveAttribute('href', '/app/auditoria')

    await screen.findByText('Katherin Mendoza')
    expect(filas()).toHaveLength(3)
    const caja = fila('Katherin Mendoza')
    expect(caja.getByText('13/10/2026')).toBeInTheDocument()
    expect(caja.getByText('18:30:00')).toBeInTheDocument()
    expect(caja.getByText('Recepción y admisión')).toBeInTheDocument()
    expect(caja.getByText('Sede Chilca')).toBeInTheDocument()
    expect(caja.getByText('Caja')).toBeInTheDocument()
    expect(caja.getByText('CERRAR_CAJA')).toBeInTheDocument()
    expect(caja.getByText('Caja cerrada con diferencia de S/ 0.00')).toBeInTheDocument()
    expect(caja.getByText('190.12.1.1')).toBeInTheDocument()

    const usuario = fila('Lucía Paredes')
    expect(usuario.getByText('General')).toBeInTheDocument()
    expect(usuario.getByText('Administración general')).toBeInTheDocument()
    expect(usuario.getByText('Usuarios')).toBeInTheDocument()
    expect(fila('Dra. Elena Paucar').getByText('Sede Huancayo Centro')).toBeInTheDocument()
    expect(screen.getByText('Página 1 de 1 · 3 eventos')).toBeInTheDocument()
    expect(consultas).toEqual([null])
  })

  it('el buscador filtra en tiempo real por usuario, detalle, acción e IP, sin distinguir tildes ni mayúsculas', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    const buscar = screen.getByLabelText('Buscar')

    await userEvent.type(buscar, 'KATHERIN')
    expect(filas()).toHaveLength(1)
    expect(screen.getByText('Katherin Mendoza')).toBeInTheDocument()

    await userEvent.clear(buscar)
    await userEvent.type(buscar, 'mario rivas') // está en el detalle
    expect(filas()).toHaveLength(1)
    expect(screen.getByText('Lucía Paredes')).toBeInTheDocument()

    await userEvent.clear(buscar)
    await userEvent.type(buscar, 'apertura_historia') // acción
    expect(screen.getByText('Dra. Elena Paucar')).toBeInTheDocument()
    expect(filas()).toHaveLength(1)

    await userEvent.clear(buscar)
    await userEvent.type(buscar, '10.0.0.5') // IP
    expect(screen.getByText('Lucía Paredes')).toBeInTheDocument()
    expect(filas()).toHaveLength(1)
    // Todo se filtra en memoria: sigue habiendo una sola consulta al servidor.
    expect(consultas).toEqual([null])
  })

  it('sin coincidencias muestra el estado vacío con los filtros', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    await userEvent.type(screen.getByLabelText('Buscar'), 'zzz-no-existe')
    expect(await screen.findByRole('heading', { name: 'No se encontraron eventos con los filtros aplicados' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('el selector de sede filtra en memoria, incluida la opción "General (sin sede)"', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    const sede = screen.getByLabelText('Sede')

    await userEvent.selectOptions(sede, 'sede-chilca')
    expect(filas()).toHaveLength(1)
    expect(screen.getByText('Katherin Mendoza')).toBeInTheDocument()

    await userEvent.selectOptions(sede, 'sede-centro')
    expect(screen.getByText('Dra. Elena Paucar')).toBeInTheDocument()
    expect(filas()).toHaveLength(1)

    await userEvent.selectOptions(sede, '__sin_sede__')
    expect(screen.getByText('Lucía Paredes')).toBeInTheDocument()
    expect(filas()).toHaveLength(1)

    // Sede + texto se combinan.
    await userEvent.selectOptions(sede, 'sede-chilca')
    await userEvent.type(screen.getByLabelText('Buscar'), 'elena')
    expect(await screen.findByRole('heading', { name: 'No se encontraron eventos con los filtros aplicados' })).toBeInTheDocument()
    expect(consultas).toEqual([null])
  })

  it('el filtro de fecha deja solo los eventos de ese día', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    fireEvent.change(screen.getByLabelText('Fecha'), { target: { value: '2026-10-12' } })
    expect(filas()).toHaveLength(1)
    expect(screen.getByText('Dra. Elena Paucar')).toBeInTheDocument()
  })

  it('cambiar el módulo vuelve a consultar al servidor con ?modulo= y muestra solo ese módulo', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Lucía Paredes')
    expect(consultas).toEqual([null])

    await userEvent.selectOptions(screen.getByLabelText('Módulo'), 'CAJA')
    await waitFor(() => expect(consultas).toEqual([null, 'CAJA']))
    await waitFor(() => expect(filas()).toHaveLength(1))
    expect(screen.getByText('Katherin Mendoza')).toBeInTheDocument()
    expect(screen.queryByText('Lucía Paredes')).not.toBeInTheDocument()
    expect(screen.getByText(/200 eventos más recientes del módulo Caja/)).toBeInTheDocument()

    // Volver a "Todos los módulos" consulta sin parámetro.
    await userEvent.selectOptions(screen.getByLabelText('Módulo'), 'TODOS')
    await waitFor(() => expect(filas()).toHaveLength(3))
  })

  it('ofrece los módulos que el backend realmente registra', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    const valores = within(screen.getByLabelText('Módulo')).getAllByRole('option').map((o) => (o as HTMLOptionElement).value)
    expect(valores).toEqual([
      'TODOS', 'USUARIOS', 'SEGURIDAD', 'PACIENTES', 'CITAS', 'CONFIGURACION', 'HISTORIA_CLINICA', 'ODONTOGRAMA',
      'IMAGENES_CLINICAS', 'TRATAMIENTOS', 'CAJA', 'PAGOS', 'GASTOS', 'INVENTARIO', 'LABORATORIO', 'SISTEMA',
    ])
  })

  it('al hacer clic en una fila abre el detalle completo, con el JSON del detalle identado', async () => {
    api()
    await renderRuta('/app/auditoria')
    await userEvent.click(await screen.findByText('Dra. Elena Paucar'))

    const dialogo = await screen.findByRole('dialog', { name: 'Detalle del evento' })
    const d = within(dialogo)
    expect(d.getByText('ev-3')).toBeInTheDocument()
    expect(d.getByText('historia_clinica_accesos')).toBeInTheDocument()
    expect(d.getByText('p-1', { selector: 'dd' })).toBeInTheDocument()
    expect(d.getByText('APERTURA_HISTORIA')).toBeInTheDocument()
    expect(d.getByText('Sede Huancayo Centro')).toBeInTheDocument()
    expect(d.getByText('181.65.2.9')).toBeInTheDocument()
    const detalle = d.getByRole('region', { name: 'Detalle completo' })
    expect(detalle).toHaveTextContent('"pacienteId": "p-1"')
    expect(detalle).toHaveTextContent('"motivo": "consulta"')

    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('un detalle de texto plano se muestra tal cual', async () => {
    api()
    await renderRuta('/app/auditoria')
    await userEvent.click(await screen.findByRole('button', { name: /Ver detalle: CERRAR_CAJA el 13\/10\/2026 a las 18:30/ }))
    const dialogo = await screen.findByRole('dialog', { name: 'Detalle del evento' })
    expect(within(dialogo).getByRole('region', { name: 'Detalle completo' })).toHaveTextContent('Caja cerrada con diferencia de S/ 0.00')
  })

  it('pagina en el cliente de 25 en 25 y vuelve a la página 1 al cambiar un filtro', async () => {
    const muchos: Evento[] = Array.from({ length: 30 }, (_, i) => ({
      ...E_CAJA,
      id: `ev-${i + 1}`,
      usuarioNombre: `Usuario ${String(i + 1).padStart(2, '0')}`,
      hora: `10:${String(i).padStart(2, '0')}:00`,
    }))
    api(muchos)
    await renderRuta('/app/auditoria')
    await screen.findByText('Usuario 01')
    expect(filas()).toHaveLength(25)
    expect(screen.getByText('Página 1 de 2 · 30 eventos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
    expect(await screen.findByText('Usuario 30')).toBeInTheDocument()
    expect(filas()).toHaveLength(5)
    expect(screen.getByText('Página 2 de 2 · 30 eventos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Buscar'), 'Usuario 0')
    expect(screen.getByText('Página 1 de 1 · 9 eventos')).toBeInTheDocument()
  })

  it('"Refrescar" vuelve a pedir los eventos', async () => {
    api()
    await renderRuta('/app/auditoria')
    await screen.findByText('Katherin Mendoza')
    await userEvent.click(screen.getByRole('button', { name: 'Refrescar' }))
    await waitFor(() => expect(consultas).toEqual([null, null]))
  })

  it('sin eventos registrados lo dice (no confunde con filtros)', async () => {
    api([])
    await renderRuta('/app/auditoria')
    expect(await screen.findByRole('heading', { name: 'Aún no hay eventos registrados' })).toBeInTheDocument()
  })

  it('si el backend falla muestra el error y permite reintentar', async () => {
    let intentos = 0
    servidor.use(
      http.get(`${API}/auditoria`, () => {
        intentos += 1
        return intentos === 1 ? errorBackend(500, 'Fallo temporal del servidor') : HttpResponse.json(BASE)
      }),
    )
    await renderRuta('/app/auditoria')
    expect(await screen.findByText('Fallo temporal del servidor')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Reintentar/ }))
    expect(await screen.findByText('Katherin Mendoza')).toBeInTheDocument()
  })

  it.each<Rol>(['RECEPCION', 'ODONTOLOGO', 'ADMIN_SEDE'])('%s es redirigido a "Sin permiso" y ni ve el menú ni consulta la API', async (rol) => {
    sesion(rol)
    api()
    await renderRuta('/app/auditoria')
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Auditoría' })).not.toBeInTheDocument()
    expect(consultas).toEqual([])
  })
})
