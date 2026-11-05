package com.odontomax.reportes.service;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.application.dto.response.*;
import com.odontomax.infrastructure.persistence.entity.*;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.infrastructure.persistence.repository.*;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import org.junit.jupiter.api.BeforeEach;
import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.odontomax.reportes.dto.ReporteCitasResponseDTO;
import com.odontomax.reportes.dto.ReporteFinancieroResponseDTO;
import com.odontomax.reportes.dto.ReporteInventarioResponseDTO;
import com.odontomax.reportes.dto.ReportePacientesResponseDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock private SpringDataPagoRepository pagoRepository;
    @Mock private SpringDataGastoRepository gastoRepository;
    @Mock private SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;
    @Mock private SpringDataComisionRadiologicaRepository comisionRepository;
    @Mock private SpringDataTratamientoRepository tratamientoRepository;
    @Mock private ConsultaPacientesModuloApi pacientesApi;
    @Mock private ConsultaCitasModuloApi consultaCitasModuloApi;
    @Mock private ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    @Mock private ConsultaSedesModuloApi consultaSedesModuloApi;
    @Mock private SpringDataProductoInventarioRepository productoInventarioRepository;
    @Mock private SpringDataInventarioStockRepository inventarioStockRepository;
    @Mock private SpringDataInventarioMovimientoRepository inventarioMovimientoRepository;
    @Mock private SpringDataTransferenciaInventarioRepository transferenciaInventarioRepository;

    @InjectMocks private ReporteService reporteService;

    private static final String SEDE = "sede-chilca-1";

    @BeforeEach
    void setUp() {
        when(consultaSedesModuloApi.obtenerNombreSede(SEDE)).thenReturn(Optional.of("Sede Chilca 1"));
    }

    @Test
    @DisplayName("Reporte financiero: calcula ingresos, gastos, métodos de pago y cuentas por cobrar")
    void obtenerReporteFinanciero_calculaMetricas() {
        LocalDate hoy = LocalDate.now();
        UUID pacId = UUID.randomUUID();
        UUID tratId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        PagoEntity p1 = PagoEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .pacienteId(pacId)
                .tratamientoId(tratId)
                .monto(new BigDecimal("150.00"))
                .metodoPago("EFECTIVO")
                .anulado(false)
                .creadoEn(Instant.now())
                .build();

        PagoEntity p2 = PagoEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .pacienteId(pacId)
                .tratamientoId(tratId)
                .monto(new BigDecimal("250.00"))
                .metodoPago("TARJETA_CREDITO")
                .anulado(false)
                .creadoEn(Instant.now())
                .build();

        GastoEntity g1 = GastoEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .categoria("OTROS")
                .monto(new BigDecimal("100.00"))
                .anulado(false)
                .creadoEn(Instant.now())
                .build();
        PagoLaboratorioEntity pagoLab = PagoLaboratorioEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .monto(new BigDecimal("25.00"))
                .metodoPago("PLIN")
                .pagadoEn(Instant.now())
                .build();

        when(pagoRepository.findBySedeIdAndRangoFechas(eq(SEDE), any(), any())).thenReturn(List.of(p1, p2));
        when(gastoRepository.findBySedeIdAndRangoFechas(eq(SEDE), any(), any())).thenReturn(List.of(g1));
        when(pagoLaboratorioRepository.findBySedeIdAndPagadoEnGreaterThanEqualAndPagadoEnLessThan(eq(SEDE), any(), any()))
                .thenReturn(List.of(pagoLab));

        PacienteResumen paciente = new PacienteResumen(pacId, null, "Juan", "Perez", null, SEDE, null, null, null, 0);
        when(pacientesApi.listarActivos(SEDE)).thenReturn(List.of(paciente));
        when(tratamientoRepository.sumCostoTotalByPacienteId(pacId)).thenReturn(new BigDecimal("600.00"));
        when(pagoRepository.sumMontoPagadoByPacienteId(pacId)).thenReturn(new BigDecimal("400.00"));

        TratamientoEntity trat = TratamientoEntity.builder().id(tratId).nombre("Ortodoncia").especialistaId(docId).build();
        when(tratamientoRepository.findById(tratId)).thenReturn(Optional.of(trat));
        when(consultaEspecialistasApi.findNombreById(docId)).thenReturn(Optional.of("Dra. Silvia"));

        ReporteFinancieroResponseDTO reporte = reporteService.obtenerReporteFinanciero(SEDE, hoy.minusDays(7), hoy);

        assertThat(reporte).isNotNull();
        assertThat(reporte.getTotalIngresos()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(reporte.getTotalGastos()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(reporte.getBalanceNeto()).isEqualByComparingTo(new BigDecimal("275.00"));
        assertThat(reporte.getTotalEfectivo()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(reporte.getTotalTarjeta()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(reporte.getCuentasPorCobrar()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(reporte.getIngresosPorMetodo()).isNotEmpty();
        assertThat(reporte.getIngresosPorDoctor()).hasSize(1);
        assertThat(reporte.getIngresosPorDoctor().get(0).getDoctorNombre()).isEqualTo("Dra. Silvia");
        assertThat(reporte.getGastosPorCategoria())
                .anySatisfy(gasto -> assertThat(gasto.getCategoria()).isEqualTo("Laboratorio"));
    }

    @Test
    @DisplayName("Reporte de citas: calcula embudo, tasa de asistencia y agrupación por día")
    void obtenerReporteCitas_calculaMetricas() {
        LocalDate hoy = LocalDate.now();
        List<CitaResponseDTO> citas = List.of(
                CitaResponseDTO.builder().id(UUID.randomUUID()).fecha(hoy).estado("ATENDIDA").sedeId(SEDE).build(),
                CitaResponseDTO.builder().id(UUID.randomUUID()).fecha(hoy).estado("ATENDIDA").sedeId(SEDE).build(),
                CitaResponseDTO.builder().id(UUID.randomUUID()).fecha(hoy).estado("CONFIRMADA").sedeId(SEDE).build(),
                CitaResponseDTO.builder().id(UUID.randomUUID()).fecha(hoy).estado("CANCELADA").sedeId(SEDE).build(),
                CitaResponseDTO.builder().id(UUID.randomUUID()).fecha(hoy).estado("FALTO").sedeId(SEDE).build()
        );

        when(consultaCitasModuloApi.findByRangoFechasYSede(any(), any(), eq(SEDE))).thenReturn(citas);

        ReporteCitasResponseDTO rep = reporteService.obtenerReporteCitas(SEDE, hoy.minusDays(1), hoy);

        assertThat(rep).isNotNull();
        assertThat(rep.getTotalCitas()).isEqualTo(5);
        assertThat(rep.getAtendidas()).isEqualTo(2);
        assertThat(rep.getConfirmadas()).isEqualTo(1);
        assertThat(rep.getCanceladas()).isEqualTo(1);
        assertThat(rep.getNoAsistio()).isEqualTo(1);
        assertThat(rep.getTasaAsistencia()).isEqualTo(40.0);
        assertThat(rep.getCitasPorDia()).isNotEmpty();
        assertThat(rep.getCitasPorEstado()).isNotEmpty();
    }

    @Test
    @DisplayName("Reporte de pacientes: agrupa fuentes de captación y calcula nuevos vs recurrentes")
    void obtenerReportePacientes_calculaMetricas() {
        LocalDate hoy = LocalDate.now();
        PacienteResumen p1 = new PacienteResumen(UUID.randomUUID(), null, "Ana", "Gomez", null, SEDE,
                hoy.minusYears(25), "Instagram", OffsetDateTime.now(), 0);
        PacienteResumen p2 = new PacienteResumen(UUID.randomUUID(), null, "Carlos", "Diaz", null, SEDE,
                hoy.minusYears(30).plusDays(5), "Recomendación", OffsetDateTime.now().minusMonths(3), 0);

        when(pacientesApi.listarActivos(SEDE)).thenReturn(List.of(p1, p2));

        ReportePacientesResponseDTO rep = reporteService.obtenerReportePacientes(SEDE, hoy.minusDays(10), hoy);

        assertThat(rep).isNotNull();
        assertThat(rep.getTotalPacientes()).isEqualTo(2);
        assertThat(rep.getPacientesNuevos()).isEqualTo(1);
        assertThat(rep.getPacientesRecurrentes()).isEqualTo(1);
        assertThat(rep.getFuentesCaptacion()).hasSize(2);
        assertThat(rep.getProximosCumpleanos()).isNotEmpty();
        assertThat(rep.getPacientesPorMes()).hasSize(6);
    }

    @Test
    @DisplayName("Reporte de inventario: detecta alertas de stock bajo y lotes por vencer")
    void obtenerReporteInventario_detectaAlertas() {
        UUID prod1Id = UUID.randomUUID();
        UUID prod2Id = UUID.randomUUID();
        LocalDate hoy = LocalDate.now();

        ProductoInventarioEntity prod1 = ProductoInventarioEntity.builder()
                .id(prod1Id).codigo("RES-001").nombre("Resina 3M").categoria("Restauración")
                .stockMinimo(10).precioUnitario(new BigDecimal("45.00")).build();
        ProductoInventarioEntity prod2 = ProductoInventarioEntity.builder()
                .id(prod2Id).codigo("ANES-002").nombre("Anestesia Lidocaína").categoria("Quirúrgico")
                .stockMinimo(20).precioUnitario(new BigDecimal("12.50")).build();

        when(productoInventarioRepository.findAll()).thenReturn(List.of(prod1, prod2));

        InventarioStockEntity st1 = InventarioStockEntity.builder().productoId(prod1Id).sedeId(SEDE).cantidad(4).build();
        InventarioStockEntity st2 = InventarioStockEntity.builder().productoId(prod2Id).sedeId(SEDE).cantidad(25).build();
        when(inventarioStockRepository.findBySedeId(SEDE)).thenReturn(List.of(st1, st2));

        InventarioMovimientoEntity mov1 = InventarioMovimientoEntity.builder()
                .id(1L).productoId(prod1Id).sedeId(SEDE).tipo("ENTRADA").cantidad(10)
                .lote("LOT-2026A").fechaVencimiento(hoy.plusDays(15)).build();
        when(inventarioMovimientoRepository.findBySedeIdOrderByCreadoEnDesc(SEDE)).thenReturn(List.of(mov1));
        when(transferenciaInventarioRepository.findBySede(SEDE)).thenReturn(List.of());

        ReporteInventarioResponseDTO rep = reporteService.obtenerReporteInventario(SEDE);

        assertThat(rep).isNotNull();
        assertThat(rep.getTotalProductos()).isEqualTo(2);
        assertThat(rep.getProductosStockBajo()).isEqualTo(1);
        assertThat(rep.getAlertasStock()).hasSize(1);
        assertThat(rep.getAlertasStock().get(0).getCodigo()).isEqualTo("RES-001");
        assertThat(rep.getProductosPorVencer()).isEqualTo(1);
        assertThat(rep.getLotesVencimiento()).hasSize(1);
        assertThat(rep.getValorizacionTotal()).isEqualByComparingTo(new BigDecimal("492.50")); // 4*45 + 25*12.50 = 180 + 312.5 = 492.5
    }

    @Test
    @DisplayName("Reporte financiero: la comisión liquidada a un doctor que deriva cuenta como egreso del periodo y de su sede")
    void obtenerReporteFinanciero_incluyeComisionesLiquidadas() {
        String sedeVillamax = SEDE; // la sede del negocio activo; el nombre ya está preparado en setUp
        PagoEntity cobro = PagoEntity.builder().id(UUID.randomUUID()).sedeId(sedeVillamax).monto(new BigDecimal("60.00"))
                .metodoPago("PLIN").anulado(false).pagadoEn(Instant.now()).creadoEn(Instant.now()).build();
        ComisionRadiologicaEntity liquidada = ComisionRadiologicaEntity.builder().id(UUID.randomUUID()).sedeId(sedeVillamax)
                .montoComision(new BigDecimal("6.00")).estado("LIQUIDADO").fechaLiquidacion(OffsetDateTime.now()).build();
        ComisionRadiologicaEntity deOtraSede = ComisionRadiologicaEntity.builder().id(UUID.randomUUID()).sedeId("sede-eltambo")
                .montoComision(new BigDecimal("99.00")).estado("LIQUIDADO").fechaLiquidacion(OffsetDateTime.now()).build();
        when(pagoRepository.findBySedeIdAndRangoFechas(eq(sedeVillamax), any(), any())).thenReturn(List.of(cobro));
        when(comisionRepository.findByEstadoAndFechaLiquidacionBetween(eq("LIQUIDADO"), any(), any()))
                .thenReturn(List.of(liquidada, deOtraSede));
        when(pacientesApi.listarActivos(sedeVillamax)).thenReturn(List.of());

        LocalDate hoy = LocalDate.now();
        ReporteFinancieroResponseDTO reporte = reporteService.obtenerReporteFinanciero(sedeVillamax, hoy.minusDays(1), hoy);

        assertThat(reporte.getTotalIngresos()).isEqualByComparingTo("60.00");
        assertThat(reporte.getTotalGastos()).isEqualByComparingTo("6.00");
        assertThat(reporte.getBalanceNeto()).isEqualByComparingTo("54.00");
        assertThat(reporte.getGastosPorCategoria()).anySatisfy(g -> {
            assertThat(g.getCategoria()).isEqualTo("Comisiones radiológicas");
            assertThat(g.getTotal()).isEqualByComparingTo("6.00");
        });
        assertThat(reporte.getIngresosPorSede()).singleElement().satisfies(s -> assertThat(s.getTotalGastos()).isEqualByComparingTo("6.00"));
    }
    @Test
    @DisplayName("Reporte financiero: ignora pagos de laboratorio anulados")
    void obtenerReporteFinanciero_ignoraPagosLaboratorioAnulados() {
        LocalDate hoy = LocalDate.now();

        PagoLaboratorioEntity pagoValido = PagoLaboratorioEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .monto(new BigDecimal("50.00"))
                .metodoPago("EFECTIVO")
                .pagadoEn(Instant.now())
                .anulado(false)
                .build();

        PagoLaboratorioEntity pagoAnulado = PagoLaboratorioEntity.builder()
                .id(UUID.randomUUID())
                .sedeId(SEDE)
                .monto(new BigDecimal("100.00"))
                .metodoPago("EFECTIVO")
                .pagadoEn(Instant.now())
                .anulado(true)
                .build();

        when(pagoRepository.findBySedeIdAndRangoFechas(eq(SEDE), any(), any())).thenReturn(List.of());
        when(gastoRepository.findBySedeIdAndRangoFechas(eq(SEDE), any(), any())).thenReturn(List.of());
        when(pagoLaboratorioRepository.findBySedeIdAndPagadoEnGreaterThanEqualAndPagadoEnLessThan(eq(SEDE), any(), any()))
                .thenReturn(List.of(pagoValido, pagoAnulado));
        when(pacientesApi.listarActivos(SEDE)).thenReturn(List.of());

        ReporteFinancieroResponseDTO reporte = reporteService.obtenerReporteFinanciero(SEDE, hoy.minusDays(7), hoy);

        assertThat(reporte).isNotNull();
        assertThat(reporte.getTotalGastos()).isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
