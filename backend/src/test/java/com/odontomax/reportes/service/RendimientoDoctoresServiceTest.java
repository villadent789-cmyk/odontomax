package com.odontomax.reportes.service;

import com.odontomax.agenda.api.ConsultaCitasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi.MetaDoctor;
import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.entity.PagoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoRepository;
import com.odontomax.reportes.dto.MiAvanceResponseDTO;
import com.odontomax.reportes.dto.RendimientoDoctoresResponseDTO;
import com.odontomax.reportes.dto.RendimientoDoctoresResponseDTO.DoctorRendimientoDTO;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import com.odontomax.sedes.api.ConsultaSedesModuloApi.ConfiguracionSedeAgendaDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RendimientoDoctoresServiceTest {

    private static final String CHILCA = "sede-chilca-1";
    private static final String CENTRO = "sede-eltambo";
    private static final String VILLAMAX = "sede-villamax-chilca";

    @Mock private SpringDataPagoRepository pagoRepository;
    @Mock private SpringDataTratamientoRepository tratamientoRepository;
    @Mock private ConsultaCitasModuloApi consultaCitasApi;
    @Mock private ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    @Mock private ConsultaSedesModuloApi consultaSedesApi;
    @Mock private com.odontomax.laboratorio.api.ConsultaLaboratorioModuloApi consultaLaboratorioApi;

    private RendimientoDoctoresService servicio;
    private final UUID doctorA = UUID.randomUUID();
    private final UUID doctorB = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        servicio = new RendimientoDoctoresService(pagoRepository, tratamientoRepository,
                consultaCitasApi, consultaEspecialistasApi, consultaSedesApi, consultaLaboratorioApi);
        when(consultaSedesApi.listarIdsSedesActivas()).thenReturn(List.of(CHILCA, CENTRO, VILLAMAX));
        when(consultaLaboratorioApi.montoLaboratorioPorDoctor(any(), any(), any())).thenReturn(java.util.Map.of());
        for (String sede : List.of(CHILCA, CENTRO)) {
            when(consultaSedesApi.obtenerConfiguracionSede(sede)).thenReturn(Optional.of(config(sede, "VILLADENT")));
        }
        when(consultaSedesApi.obtenerConfiguracionSede(VILLAMAX)).thenReturn(Optional.of(config(VILLAMAX, "VILLAMAX")));
        when(consultaSedesApi.obtenerNombreSede(any())).thenAnswer(i -> Optional.of("Nombre " + i.getArgument(0)));
        when(consultaEspecialistasApi.findNombreById(doctorA)).thenReturn(Optional.of("Doctor A"));
        when(consultaEspecialistasApi.findNombreById(doctorB)).thenReturn(Optional.of("Doctor B"));
        when(consultaEspecialistasApi.obtenerMeta(any())).thenReturn(Optional.of(new MetaDoctor(null, null)));
        when(consultaEspecialistasApi.idsActivosEnSede(any())).thenReturn(Set.of());
        when(consultaCitasApi.findByRangoFechasYSede(any(), any(), any())).thenReturn(List.of());
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("El dueño ve el cobrado por doctor y sede, la meta, el avance y la comisión; Villamax no cuenta")
    void duenoVeTodoYVillamaxQuedaFuera() {
        entrar("ADMIN_GENERAL", null);
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of(
                pago(doctorA, CHILCA, "10000"),
                pago(doctorA, CENTRO, "10000"),
                pago(doctorA, VILLAMAX, "5000"),
                anulado(pago(doctorA, CHILCA, "7000")),
                pago(doctorB, CHILCA, "3000")));
        when(consultaEspecialistasApi.obtenerMeta(doctorA))
                .thenReturn(Optional.of(new MetaDoctor(new BigDecimal("16000"), new BigDecimal("10"))));

        RendimientoDoctoresResponseDTO r = servicio.obtenerRendimiento("2026-10", null);

        assertThat(r.getMes()).isEqualTo("2026-10");
        assertThat(r.getSedes()).extracting(RendimientoDoctoresResponseDTO.SedeDTO::getId).containsExactly(CHILCA, CENTRO);
        assertThat(r.getTotalCobrado()).isEqualByComparingTo("23000");
        DoctorRendimientoDTO a = doctor(r, doctorA);
        assertThat(a.getCobrado()).isEqualByComparingTo("20000");
        assertThat(a.getCobradoPorSede().get(CHILCA)).isEqualByComparingTo("10000");
        assertThat(a.getCobradoPorSede().get(CENTRO)).isEqualByComparingTo("10000");
        assertThat(a.getCobradoPorSede()).doesNotContainKey(VILLAMAX);
        assertThat(a.getMetaMensual()).isEqualByComparingTo("16000");
        assertThat(a.getPorcentajeAvance()).isEqualByComparingTo("125.0");
        assertThat(a.getComisionEstimada()).isEqualByComparingTo("400.00");
        assertThat(a.getComisionExcedentePorcentaje()).isEqualByComparingTo("10");
        assertThat(r.getDoctores().get(0).getDoctorId()).isEqualTo(doctorA);
    }

    @Test
    @DisplayName("El administrador de sede ve solo su sede, la meta y el avance, y nunca la comisión")
    void administradorDeSedeNoVeLaComision() {
        entrar("ADMIN_SEDE", CHILCA);
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of(
                pago(doctorA, CHILCA, "4000"),
                pago(doctorA, CENTRO, "16000")));
        when(consultaEspecialistasApi.obtenerMeta(doctorA))
                .thenReturn(Optional.of(new MetaDoctor(new BigDecimal("10000"), new BigDecimal("10"))));

        RendimientoDoctoresResponseDTO r = servicio.obtenerRendimiento("2026-10", null);

        assertThat(r.getSedes()).extracting(RendimientoDoctoresResponseDTO.SedeDTO::getId).containsExactly(CHILCA);
        DoctorRendimientoDTO a = doctor(r, doctorA);
        assertThat(a.getCobrado()).isEqualByComparingTo("4000");
        assertThat(a.getCobradoPorSede()).containsOnlyKeys(CHILCA);
        assertThat(a.getMetaMensual()).isEqualByComparingTo("10000");
        assertThat(a.getPorcentajeAvance()).isEqualByComparingTo("200.0");
        assertThat(a.getComisionEstimada()).isNull();
        assertThat(a.getComisionExcedentePorcentaje()).isNull();
    }

    @Test
    @DisplayName("Un administrador de sede no puede consultar otra sede")
    void administradorDeSedeNoConsultaOtraSede() {
        entrar("ADMIN_SEDE", CHILCA);

        assertThatThrownBy(() -> servicio.obtenerRendimiento("2026-10", CENTRO))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Recepción y odontólogo no consultan el rendimiento de doctores")
    void recepcionYOdontologoSonRechazados() {
        entrar("RECEPCION", CHILCA);
        assertThatThrownBy(() -> servicio.obtenerRendimiento("2026-10", null)).isInstanceOf(AccessDeniedException.class);

        entrar("ODONTOLOGO", null);
        assertThatThrownBy(() -> servicio.obtenerRendimiento("2026-10", null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Una sede de Villamax no es una vista válida del rendimiento de Villadent")
    void sedeVillamaxEsRechazada() {
        entrar("ADMIN_GENERAL", null);

        assertThatThrownBy(() -> servicio.obtenerRendimiento("2026-10", VILLAMAX))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Calcula duración real frente a la prevista, espera y porcentaje de citas que se pasaron; ignora citas sin marcas")
    void metricasDeTiempos() {
        entrar("ADMIN_GENERAL", null);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        when(consultaCitasApi.findByRangoFechasYSede(any(), any(), eq(CHILCA))).thenReturn(List.of(
                cita(doctorA, "ATENDIDA", 30, base, base.plusSeconds(5 * 60), base.plusSeconds(5 * 60 + 20 * 60)),
                cita(doctorA, "ATENDIDA", 30, base, base.plusSeconds(15 * 60), base.plusSeconds(15 * 60 + 40 * 60)),
                cita(doctorA, "ATENDIDA", 30, null, null, null),
                cita(doctorA, "CANCELADA", 30, null, null, null),
                cita(doctorA, "NO_ASISTIO", 30, null, null, null)));

        DoctorRendimientoDTO a = doctor(servicio.obtenerRendimiento("2026-10", null), doctorA);

        assertThat(a.getCitasAtendidas()).isEqualTo(3);
        assertThat(a.getCitasCanceladas()).isEqualTo(1);
        assertThat(a.getCitasNoAsistio()).isEqualTo(1);
        assertThat(a.getDuracionRealPromedioMin()).isEqualTo(30.0);
        assertThat(a.getDuracionPrevistaPromedioMin()).isEqualTo(30.0);
        assertThat(a.getPorcentajeCitasQuePasaronDelTiempoPrevisto()).isEqualTo(50.0);
        assertThat(a.getEsperaPromedioMin()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("El mes se corta en hora de Lima: un cobro de la noche del último día cuenta en ese mes")
    void bordeDeMesEnLima() {
        entrar("ADMIN_GENERAL", null);

        servicio.obtenerRendimiento("2026-10", null);

        org.mockito.ArgumentCaptor<Instant> desde = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> hasta = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(pagoRepository).findAllByRangoFechas(desde.capture(), hasta.capture());
        assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(hasta.getValue()).isEqualTo(Instant.parse("2026-11-01T04:59:59.999Z"));
    }

    @Test
    @DisplayName("La meta es por mano de obra: el laboratorio se descuenta del cobrado antes de medir el avance")
    void laboratorioSeDescuentaDeLaMeta() {
        entrar("ADMIN_GENERAL", null);
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of(pago(doctorA, CHILCA, "20000")));
        when(consultaEspecialistasApi.obtenerMeta(doctorA))
                .thenReturn(Optional.of(new MetaDoctor(new BigDecimal("16000"), new BigDecimal("10"))));
        when(consultaLaboratorioApi.montoLaboratorioPorDoctor(any(), any(), any()))
                .thenReturn(java.util.Map.of(doctorA, new BigDecimal("5000")));

        DoctorRendimientoDTO a = doctor(servicio.obtenerRendimiento("2026-10", null), doctorA);

        assertThat(a.getCobrado()).isEqualByComparingTo("20000");
        assertThat(a.getLaboratorioDescontado()).isEqualByComparingTo("5000");
        assertThat(a.getCobradoParaMeta()).isEqualByComparingTo("15000");
        assertThat(a.getPorcentajeAvance()).isEqualByComparingTo("93.8");
        // Con 15 000 de mano de obra no se llega a los 16 000: no hay excedente.
        assertThat(a.getComisionEstimada()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("El laboratorio descontado nunca supera lo cobrado")
    void laboratorioNoSuperaLoCobrado() {
        entrar("ADMIN_GENERAL", null);
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of(pago(doctorA, CHILCA, "3000")));
        when(consultaLaboratorioApi.montoLaboratorioPorDoctor(any(), any(), any()))
                .thenReturn(java.util.Map.of(doctorA, new BigDecimal("9000")));

        DoctorRendimientoDTO a = doctor(servicio.obtenerRendimiento("2026-10", null), doctorA);

        assertThat(a.getLaboratorioDescontado()).isEqualByComparingTo("3000");
        assertThat(a.getCobradoParaMeta()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Un odontólogo ve solo su propio avance, sin comisión")
    void odontologoVeSuAvance() {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(usuarioId, "dra@odontomax.pe", "Dra. A", "ODONTOLOGO", null), null, List.of()));
        when(consultaEspecialistasApi.findEspecialistaIdByUsuarioId(usuarioId)).thenReturn(Optional.of(doctorA));
        when(consultaEspecialistasApi.obtenerMeta(doctorA))
                .thenReturn(Optional.of(new MetaDoctor(new BigDecimal("20000"), new BigDecimal("10"))));
        when(pagoRepository.findAllByRangoFechas(any(), any())).thenReturn(List.of(
                pago(doctorA, CHILCA, "14000"),
                pago(doctorB, CHILCA, "9000"),
                pago(doctorA, VILLAMAX, "3000")));

        MiAvanceResponseDTO r = servicio.obtenerMiAvance("2026-10");

        assertThat(r.getDoctorId()).isEqualTo(doctorA);
        assertThat(r.getCobrado()).isEqualByComparingTo("14000");
        assertThat(r.getMetaMensual()).isEqualByComparingTo("20000");
        assertThat(r.getPorcentajeAvance()).isEqualByComparingTo("70.0");
    }

    @Test
    @DisplayName("Solo un odontólogo con perfil de especialista consulta su avance")
    void miAvanceExigeOdontologo() {
        entrar("ADMIN_GENERAL", null);

        assertThatThrownBy(() -> servicio.obtenerMiAvance("2026-10")).isInstanceOf(AccessDeniedException.class);
    }

    private DoctorRendimientoDTO doctor(RendimientoDoctoresResponseDTO r, UUID id) {
        return r.getDoctores().stream().filter(d -> id.equals(d.getDoctorId())).findFirst().orElseThrow();
    }

    private void entrar(String rol, String sedeId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(UUID.randomUUID(), "u@odontomax.pe", "Usuario", rol, sedeId), null, List.of()));
    }

    private static ConfiguracionSedeAgendaDTO config(String id, String unidad) {
        return new ConfiguracionSedeAgendaDTO(id, id, unidad, true, null, null, BigDecimal.ZERO, 24, 24, 24,
                java.time.LocalTime.of(9, 0), 30);
    }

    private static PagoEntity pago(UUID doctor, String sede, String monto) {
        return PagoEntity.builder().sedeId(sede).especialistaId(doctor).monto(new BigDecimal(monto)).anulado(false).build();
    }

    private static PagoEntity anulado(PagoEntity p) {
        p.setAnulado(true);
        return p;
    }

    private static CitaResponseDTO cita(UUID doctor, String estado, int prevista,
                                        Instant llegada, Instant inicio, Instant fin) {
        return CitaResponseDTO.builder().doctorId(doctor).sedeId(CHILCA).estado(estado).duracionMinutos(prevista)
                .fecha(LocalDate.of(2026, 10, 5)).llegadaEn(llegada).inicioAtencionEn(inicio).finAtencionEn(fin).build();
    }
}
