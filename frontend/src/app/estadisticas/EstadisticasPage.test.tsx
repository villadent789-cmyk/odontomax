import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRootRoute, createRouter } from '@tanstack/react-router'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import { EstadisticasPage } from './EstadisticasPage'
import type {
  ReporteFinancieroDTO,
  ReporteCitasDTO,
  ReportePacientesDTO,
  ReporteDoctoresDTO,
  ReporteInventarioDTO,
  ReporteDeudoresDTO,
} from './types'

const API = 'http://localhost:3000/api/v1'

const FINANCIERO_MOCK: ReporteFinancieroDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-10',
  totalIngresos: 12500,
  totalGastos: 3200,
  balanceNeto: 9300,
  totalEfectivo: 4500,
  totalYape: 3800,
  totalTarjeta: 2700,
  totalTransferencia: 1500,
  cuentasPorCobrar: 4100,
  tratamientosConSaldoPendiente: 6,
  ingresosPorDia: [
    { fecha: '2026-10-08', ingresos: 1200, gastos: 300, balance: 900 },
    { fecha: '2026-10-09', ingresos: 1800, gastos: 500, balance: 1300 },
  ],
  ingresosPorMetodo: [
    { metodo: 'EFECTIVO', total: 4500, cantidad: 12 },
    { metodo: 'YAPE', total: 3800, cantidad: 15 },
    { metodo: 'TARJETA', total: 2700, cantidad: 6 },
    { metodo: 'TRANSFERENCIA', total: 1500, cantidad: 4 },
  ],
  ingresosPorDoctor: [
    { doctorId: 'doc-1', doctorNombre: 'Dra. Silvia Rojas', totalGenerado: 6800, cantidadTratamientos: 14 },
  ],
  ingresosPorTratamiento: [
    { procedimientoNombre: 'Ortodoncia Brackets', totalIngresos: 5200, cantidadRealizados: 8 },
  ],
  ingresosPorSede: [
    { sedeId: 'sede-chilca-1', sedeNombre: 'Sede Chilca 1', totalIngresos: 7500, totalGastos: 1900, balance: 5600 },
    { sedeId: 'sede-eltambo', sedeNombre: 'Sede El Tambo', totalIngresos: 5000, totalGastos: 1300, balance: 3700 },
  ],
  gastosPorCategoria: [
    { categoria: 'Laboratorio', total: 450 },
  ],
}

const DEUDORES_MOCK: ReporteDeudoresDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  deudaTotalAcumulada: 4100,
  totalPacientesDeudores: 2,
  pacientes: [
    {
      pacienteId: 'pac-1',
      pacienteNombre: 'Juan Carlos Quispe',
      dni: '45892314',
      telefono: '964112233',
      sedeId: 'sede-chilca-1',
      sedeNombre: 'Sede Chilca 1',
      totalTratamientos: 2500,
      totalAbonado: 1000,
      saldoPendiente: 1500,
      tratamientosPendientes: 1,
    },
    {
      pacienteId: 'pac-2',
      pacienteNombre: 'María Elena Flores',
      dni: '78239012',
      telefono: '954223344',
      sedeId: 'sede-eltambo',
      sedeNombre: 'Sede El Tambo',
      totalTratamientos: 3600,
      totalAbonado: 1000,
      saldoPendiente: 2600,
      tratamientosPendientes: 2,
    },
  ],
}

const CITAS_MOCK: ReporteCitasDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-10',
  totalCitas: 42,
  programadas: 8,
  confirmadas: 6,
  atendidas: 24,
  canceladas: 2,
  noAsistio: 2,
  reprogramadas: 3,
  tasaAsistencia: 57.14,
  citasPorDia: [
    { fecha: '2026-10-08', programadas: 2, atendidas: 5, canceladas: 0, noAsistio: 1 },
    { fecha: '2026-10-09', programadas: 3, atendidas: 7, canceladas: 1, noAsistio: 0 },
  ],
  citasPorEstado: [
    { estado: 'ATENDIDA', cantidad: 24, porcentaje: 57.14 },
    { estado: 'PROGRAMADA', cantidad: 8, porcentaje: 19.05 },
    { estado: 'CONFIRMADA', cantidad: 6, porcentaje: 14.29 },
    { estado: 'CANCELADA', cantidad: 2, porcentaje: 4.76 },
    { estado: 'FALTO', cantidad: 2, porcentaje: 4.76 },
  ],
}

const PACIENTES_MOCK: ReportePacientesDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-10',
  totalPacientes: 120,
  pacientesNuevos: 18,
  pacientesRecurrentes: 102,
  pacientesInactivos: 12,
  fuentesCaptacion: [
    { fuente: 'Instagram', cantidad: 9, porcentaje: 50.0 },
    { fuente: 'Recomendación', cantidad: 6, porcentaje: 33.3 },
    { fuente: 'Convenio', cantidad: 3, porcentaje: 16.7 },
  ],
  proximosCumpleanos: [
    {
      id: 'c-1',
      nombreCompleto: 'Ana Paula Gómez',
      dni: '71234567',
      telefono: '987654321',
      fechaNacimiento: '1998-10-14',
      edad: 28,
      sedeId: 'sede-chilca-1',
      sedeNombre: 'Sede Chilca 1',
    },
  ],
  pacientesPorMes: [
    { mes: 'May 2026', nuevos: 12, recurrentes: 80 },
    { mes: 'Jun 2026', nuevos: 15, recurrentes: 85 },
    { mes: 'Jul 2026', nuevos: 14, recurrentes: 90 },
    { mes: 'Ago 2026', nuevos: 20, recurrentes: 95 },
    { mes: 'Set 2026', nuevos: 16, recurrentes: 100 },
    { mes: 'Oct 2026', nuevos: 18, recurrentes: 102 },
  ],
}

const DOCTORES_MOCK: ReporteDoctoresDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  fechaInicio: '2026-10-01',
  fechaFin: '2026-10-10',
  doctores: [
    {
      doctorId: 'doc-1',
      nombre: 'Dra. Silvia Rojas',
      especialidad: 'Ortodoncia Especializada',
      citasAtendidas: 16,
      citasCanceladas: 1,
      citasNoAsistio: 1,
      tratamientosRealizados: 12,
      ingresosGenerados: 6800,
      ticketPromedio: 425,
      tasaEfectividad: 88.89,
    },
    {
      doctorId: 'doc-2',
      nombre: 'Dr. Roberto Mendoza',
      especialidad: 'Implantología Oral',
      citasAtendidas: 8,
      citasCanceladas: 1,
      citasNoAsistio: 1,
      tratamientosRealizados: 6,
      ingresosGenerados: 5700,
      ticketPromedio: 712.5,
      tasaEfectividad: 80.0,
    },
  ],
}

const INVENTARIO_MOCK: ReporteInventarioDTO = {
  sedeId: null,
  sedeNombre: 'Todas las Sedes',
  totalProductos: 45,
  productosStockBajo: 2,
  productosPorVencer: 3,
  productosVencidos: 1,
  transferenciasPendientes: 1,
  valorizacionTotal: 18450,
  alertasStock: [
    {
      productoId: 'prod-1',
      codigo: 'RES-001',
      nombre: 'Resina Filtek Z350 3M',
      categoria: 'Restauración',
      sedeId: 'sede-chilca-1',
      stockActual: 2,
      stockMinimo: 5,
      estado: 'BAJO',
    },
    {
      productoId: 'prod-2',
      codigo: 'ANE-002',
      nombre: 'Anestesia Scandicaine 3%',
      categoria: 'Quirúrgico',
      sedeId: 'sede-eltambo',
      stockActual: 0,
      stockMinimo: 10,
      estado: 'CRITICO',
    },
  ],
  lotesVencimiento: [
    {
      movimientoId: 101,
      productoNombre: 'Ácido Grabador 37%',
      lote: 'LOT-2026-B',
      fechaVencimiento: '2026-10-25',
      diasParaVencer: 15,
      sedeId: 'sede-chilca-1',
      estado: 'POR_VENCER',
    },
  ],
  transferencias: [
    {
      id: 'tr-1',
      productoNombre: 'Conos de Gutapercha #25',
      cantidad: 5,
      sedeOrigenId: 'sede-chilca-1',
      sedeOrigenNombre: 'Sede Chilca 1',
      sedeDestinoId: 'sede-eltambo',
      sedeDestinoNombre: 'Sede El Tambo',
      estado: 'EN_TRANSITO',
      fecha: '2026-10-09',
    },
  ],
  movimientosRecientes: [],
}

function configurarHandlersMSW() {
  servidor.use(
    http.get(`${API}/reportes/financiero`, () => {
      return HttpResponse.json(FINANCIERO_MOCK)
    }),
    http.get(`${API}/reportes/deudores`, () => {
      return HttpResponse.json(DEUDORES_MOCK)
    }),
    http.get(`${API}/reportes/citas`, () => {
      return HttpResponse.json(CITAS_MOCK)
    }),
    http.get(`${API}/reportes/pacientes`, () => {
      return HttpResponse.json(PACIENTES_MOCK)
    }),
    http.get(`${API}/reportes/doctores`, () => {
      return HttpResponse.json(DOCTORES_MOCK)
    }),
    http.get(`${API}/reportes/inventario`, () => {
      return HttpResponse.json(INVENTARIO_MOCK)
    }),
    http.get(`${API}/reportes/rendimiento-doctores`, () =>
      HttpResponse.json({ mes: '2026-10', sedes: [], totalCobrado: 0, doctores: [] }),
    ),
  )
}

describe('Fase 8 · Panel Gerencial y Reportes Ejecutivos', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    configurarHandlersMSW()
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('renderiza el dashboard general con KPIs principales como ADMIN_GENERAL', async () => {
    guardarSesion({
      token: 'jwt-admin-general',
      usuario: {
        id: 'u-admin',
        nombre: 'Administrador General',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })

    renderRuta('/app/estadisticas')

    // Título y encabezado
    expect(await screen.findByText('Panel Gerencial y Estadísticas')).toBeInTheDocument()

    // KPIs en pantalla
    expect(await screen.findByText('Ingresos del Periodo')).toBeInTheDocument()
    expect(screen.getByText('Pacientes Nuevos')).toBeInTheDocument()
    expect(screen.getByText('Citas Atendidas')).toBeInTheDocument()
    expect(screen.getByText('Cuentas por Cobrar')).toBeInTheDocument()
    expect(screen.getByText('Stock Crítico / Alertas')).toBeInTheDocument()

    // Valores formateados
    expect(await screen.findByText('S/ 12,500.00')).toBeInTheDocument()
    expect(screen.getByText('18')).toBeInTheDocument() // nuevos
    expect(screen.getByText('24')).toBeInTheDocument() // atendidas
  })

  it('permite navegar entre pestañas ejecutivas: Finanzas, Citas, Pacientes, Doctores e Inventario', async () => {
    const user = userEvent.setup()

    guardarSesion({
      token: 'jwt-admin-general',
      usuario: {
        id: 'u-admin',
        nombre: 'Administrador General',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })

    renderRuta('/app/estadisticas')

    expect(await screen.findByText('Panel Gerencial y Estadísticas')).toBeInTheDocument()

    // 1. Pestaña Finanzas
    await user.click(screen.getByRole('button', { name: /Finanzas y Caja/i }))
    expect(await screen.findByText('Distribución por Método de Pago')).toBeInTheDocument()
    expect(screen.getByText('Gastos por categoría')).toBeInTheDocument()
    expect(screen.getByText('Laboratorio')).toBeInTheDocument()
    expect(screen.getByText('Reporte de Pacientes con Saldo Deudor')).toBeInTheDocument()
    expect(screen.getByText('Juan Carlos Quispe')).toBeInTheDocument()
    expect(screen.getByText('S/ 1,500.00')).toBeInTheDocument()
    // Recaudación separada por método, cada una en su tarjeta.
    const tarjeta = (titulo: string) => {
      // Sube desde el título hasta el primer contenedor que ya muestra un monto: esa es la tarjeta.
      let nodo: HTMLElement | null = screen.getByText(titulo)
      while (nodo && !/S\/ /.test(nodo.textContent ?? '')) nodo = nodo.parentElement
      return nodo as HTMLElement
    }
    expect(tarjeta('Total Efectivo (S/)')).toHaveTextContent('S/ 4,500.00')
    expect(tarjeta('Total Efectivo (S/)')).not.toHaveTextContent('S/ 3,800.00')
    expect(tarjeta('Total Yape / Plin (S/)')).toHaveTextContent('S/ 3,800.00')
    expect(tarjeta('Total Yape / Plin (S/)')).not.toHaveTextContent('S/ 4,500.00')

    // 2. Pestaña Citas
    await user.click(screen.getByRole('button', { name: /Agenda y Citas/i }))
    expect(await screen.findByText('Comportamiento Diario de Citas')).toBeInTheDocument()
    expect(screen.getByText('Proporción por Estados')).toBeInTheDocument()
    expect(screen.getByText('Tasa de Asistencia Global:')).toBeInTheDocument()

    // 3. Pestaña Pacientes
    await user.click(screen.getByRole('button', { name: /Pacientes y Captación/i }))
    expect(await screen.findByText('Nuevos Pacientes por Mes')).toBeInTheDocument()
    expect(screen.getByText('Canales y Fuentes de Captación')).toBeInTheDocument()
    expect(screen.getByText('Próximos Cumpleaños de Pacientes')).toBeInTheDocument()
    expect(screen.getByText('Ana Paula Gómez')).toBeInTheDocument()

    // 4. Pestaña Doctores
    await user.click(screen.getByRole('button', { name: /Rendimiento Doctores/i }))
    expect(await screen.findByText('Facturación Generada por Especialista')).toBeInTheDocument()
    expect(screen.getByText('Rendimiento y Productividad Odontológica')).toBeInTheDocument()
    expect(screen.getByText('Dra. Silvia Rojas')).toBeInTheDocument()
    expect(screen.getByText('Dr. Roberto Mendoza')).toBeInTheDocument()

    // 4b. Pestaña Metas y Tiempos
    await user.click(screen.getByRole('button', { name: /Metas y Tiempos/i }))
    expect(await screen.findByText('No hay doctores con actividad en este mes.')).toBeInTheDocument()

    // 5. Pestaña Inventario
    await user.click(screen.getByRole('button', { name: /Almacén e Inventario/i }))
    expect(await screen.findByText('Alertas de Stock Mínimo y Crítico')).toBeInTheDocument()
    expect(screen.getByText('Vencimiento de Lotes Farmacéuticos')).toBeInTheDocument()
    expect(screen.getByText('Resina Filtek Z350 3M')).toBeInTheDocument()
    expect(screen.getByText('Transferencias de Insumos Inter-Sedes')).toBeInTheDocument()
  })

  it('permite alternar presets de fecha en la barra de filtros', async () => {
    const user = userEvent.setup()

    guardarSesion({
      token: 'jwt-admin-general',
      usuario: {
        id: 'u-admin',
        nombre: 'Administrador General',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })

    renderRuta('/app/estadisticas')

    expect(await screen.findByText('Panel Gerencial y Estadísticas')).toBeInTheDocument()

    // Cambiar a "Últimos 7 días"
    await user.click(screen.getByRole('button', { name: 'Últimos 7 días' }))
    // Cambiar a "Personalizado" -> muestra inputs Desde y Hasta
    await user.click(screen.getByRole('button', { name: 'Personalizado' }))
    expect(screen.getByText('Desde:')).toBeInTheDocument()
    expect(screen.getByText('Hasta:')).toBeInTheDocument()
  })

  it('restringe el acceso y muestra pantalla Sin Permiso a usuarios no autorizados (ej: ODONTOLOGO)', async () => {
    guardarSesion({
      token: 'jwt-odontologo',
      usuario: {
        id: 'u-doc',
        nombre: 'Dr. Odontólogo Especialista',
        rol: 'ODONTOLOGO',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })

    renderRuta('/app/estadisticas')

    // Al no tener permiso estadisticas, TanStack Router redirige a /app/sin-permiso o muestra SinPermisoPage
    await waitFor(() => {
      expect(screen.getByText('Sin permiso')).toBeInTheDocument()
    })
    expect(screen.getByText(/Tu rol no tiene acceso a esta sección/i)).toBeInTheDocument()
  })

  it('cambiar de rol sin desmontar la página no rompe el orden de los hooks', async () => {
    guardarSesion({
      token: 'jwt-odontologo',
      usuario: {
        id: 'u-doc',
        nombre: 'Dr. Odontólogo Especialista',
        rol: 'ODONTOLOGO',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    const router = createRouter({
      routeTree: createRootRoute({ component: EstadisticasPage }),
      history: createMemoryHistory({ initialEntries: ['/'] }),
    })
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Sin permiso')).toBeInTheDocument()

    act(() => {
      guardarSesion({
        token: 'jwt-admin-general',
        usuario: {
          id: 'u-admin',
          nombre: 'Administrador General',
          rol: 'ADMIN_GENERAL',
          sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
        },
        sedeActivaId: 'sede-chilca-1',
      })
    })

    expect(await screen.findByText('Panel Gerencial y Estadísticas')).toBeInTheDocument()
  })

  it('la pestaña "Resumen Villamax" solo aparece al trabajar en el negocio Villamax', async () => {
    const sedes = [
      { id: 'sede-chilca-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const },
      { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' as const },
    ]
    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([])),
      http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })),
    )
    guardarSesion({
      token: 'jwt-admin-general',
      usuario: { id: 'u-admin', nombre: 'Administrador General', rol: 'ADMIN_GENERAL', sedes },
      sedeActivaId: 'sede-chilca-1',
      negocioActivo: 'VILLADENT',
    })
    const clinica = await renderRuta('/app/estadisticas')
    await screen.findByText('Panel Gerencial y Estadísticas')
    expect(screen.queryByRole('button', { name: /Resumen Villamax/i })).not.toBeInTheDocument()
    clinica.unmount()

    guardarSesion({
      token: 'jwt-admin-general',
      usuario: { id: 'u-admin', nombre: 'Administrador General', rol: 'ADMIN_GENERAL', sedes },
      sedeActivaId: 'sede-villamax-chilca',
      negocioActivo: 'VILLAMAX',
    })
    await renderRuta('/app/estadisticas')
    await userEvent.click(await screen.findByRole('button', { name: /Resumen Villamax/i }))

    expect(await screen.findByText('Doctores que derivan')).toBeInTheDocument()
  })
})
