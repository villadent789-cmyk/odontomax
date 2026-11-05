package com.odontomax.radiologia.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import com.odontomax.sedes.api.ConsultaSedesModuloApi.EstudioDelCatalogo;
import com.odontomax.radiologia.dto.ComisionRadiologicaCreateRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaLiquidarRequestDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorRequestDTO;
import com.odontomax.radiologia.dto.ComisionRadiologicaResponseDTO;
import com.odontomax.radiologia.dto.DoctorDerivadorResponseDTO;
import com.odontomax.application.dto.response.ResumenComisionesResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.entity.ComisionRadiologicaEntity;
import com.odontomax.infrastructure.persistence.entity.DoctorDerivadorEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataComisionRadiologicaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataDoctorDerivadorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComisionRadiologicaServiceTest {

    @Mock private SpringDataDoctorDerivadorRepository doctorDerivadorRepository;
    @Mock private SpringDataComisionRadiologicaRepository comisionRepository;
    @Mock private ConsultaSedesModuloApi sedesApi;
    @Mock private ConsultaEspecialistasModuloApi especialistasApi;
    @Mock private CajaEgresoModuloApi cajaEgresoApi;

    @InjectMocks private ComisionRadiologicaService comisionService;

    private UUID doctorId;
    private UUID comisionId;
    private ClinicaUserPrincipal userAdmin;
    private DoctorDerivadorEntity doctorEntity;

    @BeforeEach
    void setUp() {
        doctorId = UUID.randomUUID();
        comisionId = UUID.randomUUID();
        userAdmin = new ClinicaUserPrincipal(
                UUID.randomUUID(),
                "admin.general@odontomax.test",
                "Doctor A (Director)",
                "ADMIN_GENERAL",
                "sede-villamax-chilca"
        );

        doctorEntity = DoctorDerivadorEntity.builder()
                .id(doctorId)
                .nombre("Dr. Carlos Palomino Quispe")
                .cop("COP 28410")
                .clinicaOrigen("Clínica Dental Sonrisas")
                .telefono("964123456")
                .banco("BCP / Yape")
                .numeroCuentaOYape("964123456")
                .porcentajeOTarifaFija(BigDecimal.valueOf(10.00))
                .activo(true)
                .creadoEn(OffsetDateTime.now())
                .actualizadoEn(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Debe registrar exitosamente un nuevo doctor derivador")
    void crearDoctorDerivador_exitoso() {
        DoctorDerivadorRequestDTO req = DoctorDerivadorRequestDTO.builder()
                .nombre("Dr. Walter Mendoza Chanca")
                .cop("COP 24902")
                .clinicaOrigen("Centro Odontológico Huancayo")
                .telefono("987654321")
                .banco("BBVA")
                .numeroCuentaOYape("0011-0234-0200987654")
                .porcentajeOTarifaFija(BigDecimal.valueOf(10.00))
                .activo(true)
                .build();

        when(doctorDerivadorRepository.save(any(DoctorDerivadorEntity.class))).thenAnswer(i -> {
            DoctorDerivadorEntity e = i.getArgument(0);
            e.setId(doctorId);
            return e;
        });
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));
        when(comisionRepository.sumComisionesPendientesPorDoctor(doctorId)).thenReturn(BigDecimal.ZERO);
        when(comisionRepository.findByDoctorDerivadorIdOrderByFechaEmisionDesc(doctorId)).thenReturn(Collections.emptyList());

        DoctorDerivadorResponseDTO res = comisionService.crearDoctorDerivador(req);

        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(doctorId);
        assertThat(res.getNombre()).isEqualTo("Dr. Carlos Palomino Quispe");
        verify(doctorDerivadorRepository).save(any(DoctorDerivadorEntity.class));
    }

    @Test
    @DisplayName("Debe registrar una comisión radiológica en estado PENDIENTE")
    void registrarComision_exitoso() {
        autenticar(userAdmin);
        ComisionRadiologicaCreateRequestDTO req = ComisionRadiologicaCreateRequestDTO.builder()
                .doctorDerivadorId(doctorId)
                .pacienteNombre("Luciana Meza Cárdenas")
                .tipoEstudio("PANORAMICA")
                .precioCobrado(BigDecimal.valueOf(50.00))
                .montoComision(BigDecimal.valueOf(10.00))
                .sedeId("sede-villamax-chilca")
                .nota("Orden física sellada por doctor")
                .build();

        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));
        when(comisionRepository.save(any(ComisionRadiologicaEntity.class))).thenAnswer(i -> {
            ComisionRadiologicaEntity c = i.getArgument(0);
            c.setId(comisionId);
            return c;
        });

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(req, userAdmin);

        assertThat(res).isNotNull();
        assertThat(res.getEstado()).isEqualTo("PENDIENTE");
        assertThat(res.getPrecioCobrado()).isEqualByComparingTo("50.00");
        assertThat(res.getMontoComision()).isEqualByComparingTo("10.00");
        assertThat(res.getDoctorDerivadorNombre()).isEqualTo("Dr. Carlos Palomino Quispe");
        verify(comisionRepository).save(any(ComisionRadiologicaEntity.class));
    }

    @Test
    @DisplayName("Debe liquidar una comisión pendiente cambiando estado a LIQUIDADO con método de pago")
    void liquidarComision_exitoso() {
        ComisionRadiologicaEntity comisionPendiente = ComisionRadiologicaEntity.builder()
                .id(comisionId)
                .doctorDerivadorId(doctorId)
                .pacienteNombre("Luciana Meza Cárdenas")
                .tipoEstudio("PANORAMICA")
                .precioCobrado(BigDecimal.valueOf(50.00))
                .montoComision(BigDecimal.valueOf(10.00))
                .estado("PENDIENTE")
                .build();

        ComisionRadiologicaLiquidarRequestDTO req = ComisionRadiologicaLiquidarRequestDTO.builder()
                .metodoPago("YAPE")
                .codigoOperacion("OP-789012")
                .nota("Yape realizado con éxito")
                .build();

        when(comisionRepository.findById(comisionId)).thenReturn(Optional.of(comisionPendiente));
        when(comisionRepository.save(any(ComisionRadiologicaEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));

        ComisionRadiologicaResponseDTO res = comisionService.liquidarComision(comisionId, req, userAdmin);

        assertThat(res).isNotNull();
        assertThat(res.getEstado()).isEqualTo("LIQUIDADO");
        assertThat(res.getMetodoPago()).isEqualTo("YAPE");
        assertThat(res.getCodigoOperacion()).isEqualTo("OP-789012");
        assertThat(res.getFechaLiquidacion()).isNotNull();
        verify(cajaEgresoApi).registrarEgresoDeSistema(any(), eq(BigDecimal.valueOf(10.00)), eq("YAPE"),
                contains("Dr. Carlos Palomino Quispe"), eq("COMISION_RADIOLOGICA"), eq(comisionId));
    }

    @Test
    @DisplayName("Debe rechazar la liquidación si la comisión ya está liquidada")
    void liquidarComision_yaLiquidada_error() {
        ComisionRadiologicaEntity comisionLiquidada = ComisionRadiologicaEntity.builder()
                .id(comisionId)
                .doctorDerivadorId(doctorId)
                .pacienteNombre("Gonzalo Astuvilca")
                .tipoEstudio("TOMOGRAFIA_3D_CONE_BEAM")
                .precioCobrado(BigDecimal.valueOf(180.00))
                .montoComision(BigDecimal.valueOf(35.00))
                .estado("LIQUIDADO")
                .build();

        ComisionRadiologicaLiquidarRequestDTO req = ComisionRadiologicaLiquidarRequestDTO.builder()
                .metodoPago("PLIN")
                .build();

        when(comisionRepository.findById(comisionId)).thenReturn(Optional.of(comisionLiquidada));

        assertThatThrownBy(() -> comisionService.liquidarComision(comisionId, req, userAdmin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya ha sido liquidada");
    }

    @Test
    @DisplayName("Debe calcular el resumen global de comisiones pendientes y liquidadas")
    void obtenerResumen_calculoCorrecto() {
        ComisionRadiologicaEntity c1 = ComisionRadiologicaEntity.builder()
                .montoComision(BigDecimal.valueOf(10.00))
                .estado("PENDIENTE")
                .build();
        ComisionRadiologicaEntity c2 = ComisionRadiologicaEntity.builder()
                .montoComision(BigDecimal.valueOf(35.00))
                .estado("PENDIENTE")
                .build();
        ComisionRadiologicaEntity c3 = ComisionRadiologicaEntity.builder()
                .montoComision(BigDecimal.valueOf(12.00))
                .estado("LIQUIDADO")
                .build();

        when(comisionRepository.findAll()).thenReturn(List.of(c1, c2, c3));
        when(doctorDerivadorRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(doctorEntity));

        ResumenComisionesResponseDTO resumen = comisionService.obtenerResumen();

        assertThat(resumen).isNotNull();
        assertThat(resumen.getTotalMontoPendiente()).isEqualByComparingTo("45.00");
        assertThat(resumen.getCantidadPendientes()).isEqualTo(2L);
        assertThat(resumen.getTotalMontoLiquidado()).isEqualByComparingTo("12.00");
        assertThat(resumen.getCantidadLiquidados()).isEqualTo(1L);
        assertThat(resumen.getTotalDoctoresDerivadoresActivos()).isEqualTo(1L);
    }

    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(ClinicaUserPrincipal user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private ComisionRadiologicaCreateRequestDTO.ComisionRadiologicaCreateRequestDTOBuilder derivacion() {
        return ComisionRadiologicaCreateRequestDTO.builder()
                .doctorDerivadorId(doctorId)
                .pacienteNombre("Luciana Meza Cárdenas")
                .sedeId("sede-villamax-chilca");
    }

    private void guardarEcho() {
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));
        when(comisionRepository.save(any(ComisionRadiologicaEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Con el estudio del catálogo, nombre, precio y comisión salen de él: S/ 60 al 12,5 % = S/ 7,50")
    void registrarComision_conEstudio_calculaDesdeElCatalogo() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-panoramica"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-panoramica", "Radiografía panorámica",
                        new BigDecimal("60.00"), new BigDecimal("12.50"), null)));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-panoramica").build(), userAdmin);

        assertThat(res.getTipoEstudio()).isEqualTo("Radiografía panorámica");
        assertThat(res.getPrecioCobrado()).isEqualByComparingTo("60.00");
        // 12.5 % del estudio, no el 10 % del doctor derivador
        assertThat(res.getMontoComision()).isEqualByComparingTo("7.50");
        assertThat(res.getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("Un estudio sin porcentaje usa el del doctor derivador")
    void registrarComision_estudioSinPorcentaje_usaElDelDerivador() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-tomografia-3d"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-tomografia-3d", "Tomografía 3D", new BigDecimal("250.00"), null, null)));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-tomografia-3d").build(), userAdmin);

        assertThat(res.getMontoComision()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("Comisión fija por estudio multiplicada por la cantidad: 10 radiografías de S/ 10 = S/ 100")
    void registrarComision_fijaPorCantidad() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-periapical"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-periapical", "Radiografía periapical",
                        new BigDecimal("15.00"), new BigDecimal("10.00"), new BigDecimal("10.00"))));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-periapical").cantidad(10).build(), userAdmin);

        assertThat(res.getMontoComision()).isEqualByComparingTo("100.00");
        assertThat(res.getPrecioCobrado()).isEqualByComparingTo("150.00");
        assertThat(res.getCantidad()).isEqualTo(10);
        assertThat(res.getEsEstudiante()).isFalse();
    }

    @Test
    @DisplayName("Una tomografía paga S/ 40 de comisión fija, sin importar el porcentaje")
    void registrarComision_tomografiaFija() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-tomografia-3d"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-tomografia-3d", "Tomografía 3D Cone Beam",
                        new BigDecimal("250.00"), new BigDecimal("10.00"), new BigDecimal("40.00"))));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-tomografia-3d").build(), userAdmin);

        assertThat(res.getMontoComision()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Paciente estudiante: precio manual y sin comisión")
    void registrarComision_estudianteSinComision() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-tomografia-3d"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-tomografia-3d", "Tomografía 3D Cone Beam",
                        new BigDecimal("250.00"), null, new BigDecimal("40.00"))));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-tomografia-3d").esEstudiante(true)
                        .precioCobrado(new BigDecimal("120.00")).build(), userAdmin);

        assertThat(res.getPrecioCobrado()).isEqualByComparingTo("120.00");
        assertThat(res.getMontoComision()).isEqualByComparingTo("0");
        assertThat(res.getEsEstudiante()).isTrue();
    }

    @Test
    @DisplayName("Paciente estudiante sin precio manual se rechaza")
    void registrarComision_estudianteSinPrecio_rechazado() {
        autenticar(userAdmin);
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));

        assertThatThrownBy(() -> comisionService.registrarComision(
                derivacion().estudioId("vmx-tomografia-3d").esEstudiante(true).build(), userAdmin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("precio");
    }

    @Test
    @DisplayName("Lo escrito a mano manda sobre el catálogo (precio acordado distinto)")
    void registrarComision_valoresManualesMandan() {
        autenticar(userAdmin);
        guardarEcho();
        when(sedesApi.obtenerEstudioDeVillamax("vmx-panoramica"))
                .thenReturn(Optional.of(new EstudioDelCatalogo("vmx-panoramica", "Radiografía panorámica",
                        new BigDecimal("60.00"), new BigDecimal("10.00"), null)));

        ComisionRadiologicaResponseDTO res = comisionService.registrarComision(
                derivacion().estudioId("vmx-panoramica").precioCobrado(new BigDecimal("50.00"))
                        .montoComision(new BigDecimal("8.00")).build(), userAdmin);

        assertThat(res.getPrecioCobrado()).isEqualByComparingTo("50.00");
        assertThat(res.getMontoComision()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("Sin estudio del catálogo hacen falta tipo, precio y comisión; un estudio inexistente se rechaza")
    void registrarComision_datosInsuficientesOEstudioInexistente() {
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));

        assertThatThrownBy(() -> comisionService.registrarComision(derivacion().build(), userAdmin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Indica el estudio");
        assertThatThrownBy(() -> comisionService.registrarComision(derivacion().estudioId("no-existe").build(), userAdmin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe en el catálogo");
        verify(comisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("La comisión solo se liquida por Efectivo, Yape o Plin y sin tocar la caja si algo falla")
    void liquidarComision_metodoFueraDeCaja_error() {
        ComisionRadiologicaEntity pendiente = ComisionRadiologicaEntity.builder().id(comisionId).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.TEN).estado("PENDIENTE").build();
        when(comisionRepository.findById(comisionId)).thenReturn(Optional.of(pendiente));

        assertThatThrownBy(() -> comisionService.liquidarComision(comisionId,
                ComisionRadiologicaLiquidarRequestDTO.builder().metodoPago("TRANSFERENCIA").build(), userAdmin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("EFECTIVO, YAPE o PLIN");
        assertThat(pendiente.getEstado()).isEqualTo("PENDIENTE");
        verifyNoInteractions(cajaEgresoApi);
    }

    @Test
    @DisplayName("Una comisión de monto cero se liquida sin movimiento de caja")
    void liquidarComision_montoCero_noMueveCaja() {
        ComisionRadiologicaEntity cero = ComisionRadiologicaEntity.builder().id(comisionId).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.ZERO).estado("PENDIENTE").build();
        when(comisionRepository.findById(comisionId)).thenReturn(Optional.of(cero));
        when(comisionRepository.save(any(ComisionRadiologicaEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(doctorDerivadorRepository.findById(doctorId)).thenReturn(Optional.of(doctorEntity));

        comisionService.liquidarComision(comisionId, ComisionRadiologicaLiquidarRequestDTO.builder().metodoPago("PLIN").build(), userAdmin);

        verifyNoInteractions(cajaEgresoApi);
    }

    @Test
    @DisplayName("Un odontólogo registra derivaciones solo en la sede de Villamax donde atiende")
    void registrarComision_odontologo_soloEnSuSede() {
        UUID usuarioId = UUID.randomUUID();
        UUID especialistaId = UUID.randomUUID();
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(usuarioId, "doc@odontomax.pe", "Doctor", "ODONTOLOGO", null);
        guardarEcho();
        when(especialistasApi.findEspecialistaIdByUsuarioId(usuarioId)).thenReturn(Optional.of(especialistaId));
        when(especialistasApi.idsActivosEnSede("sede-villamax-chilca")).thenReturn(java.util.Set.of(especialistaId));
        when(especialistasApi.idsActivosEnSede("sede-chilca-1")).thenReturn(java.util.Set.of());
        ComisionRadiologicaCreateRequestDTO manual = derivacion().tipoEstudio("PANORAMICA")
                .precioCobrado(BigDecimal.TEN).montoComision(BigDecimal.ONE).build();

        assertThat(comisionService.registrarComision(manual, doctor).getEstado()).isEqualTo("PENDIENTE");

        manual.setSedeId("sede-chilca-1");
        assertThatThrownBy(() -> comisionService.registrarComision(manual, doctor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Quien tiene otra sede no ve ni liquida comisiones de Villamax")
    void usuarioDeOtraSede_noVeComisionesDeVillamax() {
        ClinicaUserPrincipal recepcionClinica = new ClinicaUserPrincipal(UUID.randomUUID(), "r@odontomax.pe", "Recepción", "RECEPCION", "sede-chilca-1");
        autenticar(recepcionClinica);
        ComisionRadiologicaEntity deVillamax = ComisionRadiologicaEntity.builder().id(comisionId).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.TEN).estado("PENDIENTE").sedeId("sede-villamax-chilca").build();
        when(comisionRepository.findAllByOrderByFechaEmisionDesc()).thenReturn(List.of(deVillamax));
        when(comisionRepository.findById(comisionId)).thenReturn(Optional.of(deVillamax));
        when(comisionRepository.findAll()).thenReturn(List.of(deVillamax));
        when(doctorDerivadorRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of());

        assertThat(comisionService.listarComisiones(null, null)).isEmpty();
        assertThat(comisionService.obtenerResumen().getCantidadPendientes()).isZero();
        assertThatThrownBy(() -> comisionService.obtenerComisionPorId(comisionId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> comisionService.liquidarComision(comisionId,
                ComisionRadiologicaLiquidarRequestDTO.builder().metodoPago("YAPE").build(), recepcionClinica))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("El monto de la comisión solo lo ve el administrador general; el resto recibe el campo vacío")
    void montoDeComision_soloLoVeElAdministradorGeneral() {
        ComisionRadiologicaEntity propia = ComisionRadiologicaEntity.builder().id(comisionId).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.TEN).estado("PENDIENTE").sedeId("sede-villamax-chilca").build();
        when(comisionRepository.findAllByOrderByFechaEmisionDesc()).thenReturn(List.of(propia));
        when(doctorDerivadorRepository.findAll()).thenReturn(List.of(doctorEntity));

        autenticar(userAdmin);
        assertThat(comisionService.listarComisiones(null, null)).extracting(ComisionRadiologicaResponseDTO::getMontoComision)
                .containsExactly(BigDecimal.TEN);

        for (String rol : List.of("ADMIN_SEDE", "ODONTOLOGO", "RECEPCION")) {
            UUID usuarioId = UUID.randomUUID();
            UUID especialistaId = UUID.randomUUID();
            autenticar(new ClinicaUserPrincipal(usuarioId, "u@odontomax.pe", "Usuario", rol, "sede-villamax-chilca"));
            lenient().when(especialistasApi.findEspecialistaIdByUsuarioId(usuarioId)).thenReturn(Optional.of(especialistaId));
            lenient().when(especialistasApi.idsActivosEnSede("sede-villamax-chilca")).thenReturn(java.util.Set.of(especialistaId));
            assertThat(comisionService.listarComisiones(null, null)).as(rol)
                    .extracting(ComisionRadiologicaResponseDTO::getMontoComision).containsOnlyNulls();
        }
    }

    @Test
    @DisplayName("El odontólogo de Villamax ve las comisiones de su sede, no las de otra")
    void odontologo_veSoloLasComisionesDeSuSede() {
        UUID usuarioId = UUID.randomUUID();
        UUID especialistaId = UUID.randomUUID();
        autenticar(new ClinicaUserPrincipal(usuarioId, "doc@odontomax.pe", "Doctor", "ODONTOLOGO", null));
        when(especialistasApi.findEspecialistaIdByUsuarioId(usuarioId)).thenReturn(Optional.of(especialistaId));
        when(especialistasApi.idsActivosEnSede("sede-villamax-chilca")).thenReturn(java.util.Set.of(especialistaId));
        when(especialistasApi.idsActivosEnSede("sede-chilca-1")).thenReturn(java.util.Set.of());
        ComisionRadiologicaEntity propia = ComisionRadiologicaEntity.builder().id(comisionId).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.TEN).estado("PENDIENTE").sedeId("sede-villamax-chilca").build();
        ComisionRadiologicaEntity ajena = ComisionRadiologicaEntity.builder().id(UUID.randomUUID()).doctorDerivadorId(doctorId)
                .montoComision(BigDecimal.TEN).estado("PENDIENTE").sedeId("sede-chilca-1").build();
        when(comisionRepository.findAllByOrderByFechaEmisionDesc()).thenReturn(List.of(propia, ajena));
        when(doctorDerivadorRepository.findAll()).thenReturn(List.of(doctorEntity));

        assertThat(comisionService.listarComisiones(null, null)).extracting(ComisionRadiologicaResponseDTO::getId)
                .containsExactly(comisionId);
    }
}
