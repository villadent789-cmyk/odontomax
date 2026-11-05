package com.odontomax.laboratorio.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.caja.api.CajaEgresoModuloApi;
import org.springframework.security.access.AccessDeniedException;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.laboratorio.dto.LaboratorioExternoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCambioEstadoRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioCreateRequestDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioUpdateRequestDTO;
import com.odontomax.laboratorio.dto.LaboratorioExternoResponseDTO;
import com.odontomax.laboratorio.dto.OrdenLaboratorioResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.entity.LaboratorioExternoEntity;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioEntity;
import com.odontomax.infrastructure.persistence.entity.PagoLaboratorioEntity;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioHistorialEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataLaboratorioExternoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioHistorialRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoLaboratorioRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException;
import com.odontomax.laboratorio.dto.AnularPagoLaboratorioRequestDTO;
import com.odontomax.laboratorio.dto.PagoLaboratorioResponseDTO;
import com.odontomax.laboratorio.dto.RegistrarPagoLaboratorioRequestDTO;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LaboratorioServiceTest {

    @Mock private SpringDataLaboratorioExternoRepository laboratorioRepository;
    @Mock private SpringDataOrdenLaboratorioRepository ordenRepository;
    @Mock private SpringDataOrdenLaboratorioHistorialRepository historialRepository;
    @Mock private ConsultaPacientesModuloApi pacientesApi;
    @Mock private SpringDataEspecialistaRepository especialistaRepository;
    @Mock private SpringDataAuditoriaRepository auditoriaRepository;
    @Mock private ConsultaEspecialistasModuloApi consultaEspecialistasApi;
    @Mock private SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;
    @Mock private com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioPruebaRepository pruebaRepository;
    @Mock private CajaEgresoModuloApi cajaEgresoModuloApi;

    @InjectMocks private LaboratorioService laboratorioService;

    private UUID pacienteId;
    private UUID especialistaId;
    private UUID laboratorioId;
    private ClinicaUserPrincipal userAdmin;

    @BeforeEach
    void setUp() {
        pacienteId = UUID.randomUUID();
        especialistaId = UUID.randomUUID();
        laboratorioId = UUID.randomUUID();
        userAdmin = new ClinicaUserPrincipal(
                UUID.randomUUID(),
                "admin@odontomax.pe",
                "Dra. Administradora",
                "ADMIN_GENERAL",
                "sede-chilca-1"
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Crear orden genera correlativo LAB-YYYY-XXXX y registra historial inicial")
    void crearOrden_exitoso() {
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.of(
                new PacienteResumen(pacienteId, "44556677", "María", "Flores", null, null, null, null, null, 0)));
        when(especialistaRepository.findById(especialistaId)).thenReturn(Optional.of(EspecialistaEntity.builder()
                .id(especialistaId).nombre("Dr. Alex Ríos").cop("45890").build()));
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.of(LaboratorioExternoEntity.builder()
                .id(laboratorioId).nombre("Lab Huancayo Dental").tiempoEntregaPromedioDias(4).build()));

        when(ordenRepository.count()).thenReturn(0L);
        when(ordenRepository.findByCodigo(any())).thenReturn(Optional.empty());
        when(ordenRepository.save(any(OrdenLaboratorioEntity.class))).thenAnswer(inv -> {
            OrdenLaboratorioEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        OrdenLaboratorioCreateRequestDTO req = OrdenLaboratorioCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .sedeId("sede-chilca-1")
                .laboratorioId(laboratorioId)
                .tipoTrabajo("Corona Zirconio Monolítico")
                .colorTono("A2")
                .piezasDentales("11, 21")
                .costoLaboratorio(new BigDecimal("180.00"))
                .precioPaciente(new BigDecimal("450.00"))
                .indicacionesTecnicas("Bisel subgingival de 0.5mm")
                .build();

        OrdenLaboratorioResponseDTO res = laboratorioService.crearOrden(req, userAdmin);

        assertThat(res).isNotNull();
        assertThat(res.getCodigo()).startsWith("LAB-");
        assertThat(res.getEstado()).isEqualTo("ENVIADO");
        assertThat(res.getTipoTrabajo()).isEqualTo("Corona Zirconio Monolítico");
        assertThat(res.getPacienteNombre()).isEqualTo("María Flores");

        verify(historialRepository).save(argThat(h ->
                h.getEstadoAnterior() == null &&
                "ENVIADO".equals(h.getEstadoNuevo()) &&
                "Dra. Administradora".equals(h.getUsuarioNombre())
        ));
    }

    @Test
    @DisplayName("Transición de ENVIADO a EN_PRUEBA registra historial y la fecha de recepción")
    void cambiarEstado_exitoso() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .codigo("LAB-2026-0001")
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .sedeId("sede-chilca-1")
                .laboratorioId(laboratorioId)
                .tipoTrabajo("Prótesis Total Removible")
                .estado("ENVIADO")
                .fechaSolicitud(LocalDate.now())
                .build();

        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(ordenRepository.save(any(OrdenLaboratorioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historialRepository.findByOrdenIdOrderByCreadoEnAsc(ordenId)).thenReturn(Collections.emptyList());

        OrdenLaboratorioCambioEstadoRequestDTO cambio = OrdenLaboratorioCambioEstadoRequestDTO.builder()
                .nuevoEstado("EN_PRUEBA")
                .motivo("Enviado con guía de remisión G-402")
                .guiaRemision("G-402")
                .build();

        OrdenLaboratorioResponseDTO res = laboratorioService.cambiarEstado(ordenId, cambio, userAdmin);

        assertThat(res.getEstado()).isEqualTo("EN_PRUEBA");
        assertThat(orden.getFechaRecepcionClinica()).isNotNull();
        assertThat(orden.getGuiaRemision()).isEqualTo("G-402");

        verify(historialRepository).save(argThat(h ->
                "ENVIADO".equals(h.getEstadoAnterior()) &&
                "EN_PRUEBA".equals(h.getEstadoNuevo()) &&
                h.getMotivo().contains("G-402")
        ));
    }

    @Test
    @DisplayName("No permite cambiar estado si la orden ya está cancelada")
    void cambiarEstado_ordenCancelada_lanzaConflicto() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .codigo("LAB-2026-0002")
                .estado("CANCELADO")
                .build();

        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));

        OrdenLaboratorioCambioEstadoRequestDTO cambio = OrdenLaboratorioCambioEstadoRequestDTO.builder()
                .nuevoEstado("EN_PRUEBA")
                .motivo("Reactivación")
                .build();

        assertThatThrownBy(() -> laboratorioService.cambiarEstado(ordenId, cambio, userAdmin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelada");
    }

    @Test
    @DisplayName("Catálogo de laboratorios externos permite crear y listar")
    void proveedoresLaboratorio_crud() {
        LaboratorioExternoEntity labEntity = LaboratorioExternoEntity.builder()
                .id(UUID.randomUUID())
                .nombre("Laboratorio Estética Huancayo")
                .contacto("Juan Pérez")
                .tiempoEntregaPromedioDias(4)
                .activo(true)
                .build();

        when(laboratorioRepository.save(any(LaboratorioExternoEntity.class))).thenReturn(labEntity);
        when(laboratorioRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(labEntity));

        LaboratorioExternoRequestDTO req = LaboratorioExternoRequestDTO.builder()
                .nombre("Laboratorio Estética Huancayo")
                .contacto("Juan Pérez")
                .tiempoEntregaPromedioDias(4)
                .activo(true)
                .build();

        LaboratorioExternoResponseDTO creado = laboratorioService.crearLaboratorioExterno(req, userAdmin);
        assertThat(creado.getNombre()).isEqualTo("Laboratorio Estética Huancayo");

        List<LaboratorioExternoResponseDTO> lista = laboratorioService.listarLaboratoriosExternos(true);
        assertThat(lista).hasSize(1);
    }

    @Test
    @DisplayName("Obtener orden por id de paciente ajeno lanza AccessDeniedException para odontólogo")
    void obtenerOrdenPorId_pacienteAjeno_lanzaAccessDenied() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .codigo("LAB-2026-0001")
                .tipoTrabajo("Corona Zirconio")
                .estado("ENVIADO")
                .build();

        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThatThrownBy(() -> laboratorioService.obtenerOrdenPorId(ordenId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no está asignado");

        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("Crear orden para paciente ajeno lanza AccessDeniedException")
    void crearOrden_pacienteAjeno_lanzaAccessDenied() {
        OrdenLaboratorioCreateRequestDTO req = OrdenLaboratorioCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .sedeId("sede-chilca-1")
                .tipoTrabajo("Corona Zirconio")
                .build();

        doThrow(new AccessDeniedException("Acceso denegado"))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        assertThatThrownBy(() -> laboratorioService.crearOrden(req, userAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
        verify(ordenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar orden de paciente ajeno lanza AccessDeniedException")
    void actualizarOrden_pacienteAjeno_lanzaAccessDenied() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .codigo("LAB-2026-0001")
                .tipoTrabajo("Corona")
                .estado("ENVIADO")
                .build();

        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        doThrow(new AccessDeniedException("Acceso denegado"))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        OrdenLaboratorioUpdateRequestDTO req = OrdenLaboratorioUpdateRequestDTO.builder()
                .laboratorioId(laboratorioId)
                .tipoTrabajo("Corona")
                .build();

        assertThatThrownBy(() -> laboratorioService.actualizarOrden(ordenId, req, userAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("Cambiar estado de orden de paciente ajeno lanza AccessDeniedException")
    void cambiarEstado_pacienteAjeno_lanzaAccessDenied() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .codigo("LAB-2026-0001")
                .tipoTrabajo("Corona")
                .estado("ENVIADO")
                .build();

        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        doThrow(new AccessDeniedException("Acceso denegado"))
                .when(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);

        OrdenLaboratorioCambioEstadoRequestDTO req = OrdenLaboratorioCambioEstadoRequestDTO.builder()
                .nuevoEstado("EN_PRUEBA")
                .motivo("Envío")
                .build();

        assertThatThrownBy(() -> laboratorioService.cambiarEstado(ordenId, req, userAdmin))
                .isInstanceOf(AccessDeniedException.class);

        verify(consultaEspecialistasApi).exigirAccesoDeOdontologo(pacienteId);
    }

    @Test
    @DisplayName("Listar órdenes para odontólogo filtra por sus pacientes en una sola consulta batch")
    void listarOrdenes_odontologo_filtraPorPacientesDelDoctor_sinNMasUno() {
        UUID doctorUserId = UUID.randomUUID();
        ClinicaUserPrincipal userOdonto = new ClinicaUserPrincipal(
                doctorUserId, "doctor@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");

        UUID pacPropio = UUID.randomUUID();
        UUID pacAjeno = UUID.randomUUID();

        OrdenLaboratorioEntity ordenPropia = OrdenLaboratorioEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacPropio)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .codigo("LAB-2026-0001")
                .tipoTrabajo("Corona Propia")
                .estado("ENVIADO")
                .build();

        OrdenLaboratorioEntity ordenAjena = OrdenLaboratorioEntity.builder()
                .id(UUID.randomUUID())
                .pacienteId(pacAjeno)
                .especialistaId(especialistaId)
                .laboratorioId(laboratorioId)
                .codigo("LAB-2026-0002")
                .tipoTrabajo("Corona Ajena")
                .estado("ENVIADO")
                .build();

        when(ordenRepository.buscarOrdenes(eq("sede-chilca-1"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(ordenPropia, ordenAjena));
        when(consultaEspecialistasApi.pacientesDelOdontologo(doctorUserId)).thenReturn(Set.of(pacPropio));
        when(pacientesApi.findAllById(Set.of(pacPropio))).thenReturn(Collections.emptyMap());
        when(especialistaRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(laboratorioRepository.findAllById(any())).thenReturn(Collections.emptyList());

        List<OrdenLaboratorioResponseDTO> res = laboratorioService.listarOrdenes(null, null, null, null, userOdonto);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getId()).isEqualTo(ordenPropia.getId());
        assertThat(res.get(0).getCostoLaboratorio()).isNull();
        assertThat(res.get(0).getPrecioPaciente()).isNull();
        assertThat(res.get(0).getGanancia()).isNull();
        assertThat(res.get(0).getEstadoPago()).isNull();
        assertThat(res.get(0).getMontoPagado()).isNull();
        assertThat(res.get(0).getSaldoLaboratorio()).isNull();
        verify(consultaEspecialistasApi, times(1)).pacientesDelOdontologo(doctorUserId);
        verify(consultaEspecialistasApi, never()).odontologoAtiendeAlPaciente(any(), any());
        verifyNoInteractions(pagoLaboratorioRepository);
    }

    private OrdenLaboratorioEntity ordenEnCurso(UUID ordenId, String estado) {
        return OrdenLaboratorioEntity.builder()
                .id(ordenId).codigo("LAB-2026-0007").pacienteId(pacienteId).especialistaId(especialistaId)
                .sedeId("sede-chilca-1").laboratorioId(laboratorioId).tipoTrabajo("Corona").estado(estado).build();
    }

    private void prepararPrueba(UUID ordenId, OrdenLaboratorioEntity orden, long pruebasPrevias) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        when(ordenRepository.findByIdForUpdate(ordenId)).thenReturn(Optional.of(orden));
        when(ordenRepository.save(any(OrdenLaboratorioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pruebaRepository.countByOrdenId(ordenId)).thenReturn(pruebasPrevias);
        when(pruebaRepository.save(any(com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioPruebaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Una prueba que necesita ajuste devuelve el trabajo al laboratorio y la siguiente será la prueba 2")
    void registrarPrueba_conAjuste_vuelveAlLaboratorio() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = ordenEnCurso(ordenId, "ENVIADO");
        prepararPrueba(ordenId, orden, 0);

        OrdenLaboratorioResponseDTO res = laboratorioService.registrarPrueba(ordenId,
                com.odontomax.laboratorio.dto.RegistrarPruebaLaboratorioRequestDTO.builder()
                        .resultado("NECESITA_AJUSTE").notas("Alto en la cúspide").nuevaFechaEntrega(LocalDate.now().plusDays(3)).build(),
                userAdmin);

        assertThat(res.getEstado()).isEqualTo("ENVIADO");
        assertThat(orden.getFechaEstimadaEntrega()).isEqualTo(LocalDate.now().plusDays(3));
        verify(pruebaRepository).save(argThat(pr -> pr.getNumero() == 1 && "NECESITA_AJUSTE".equals(pr.getResultado())));
        verify(historialRepository).save(argThat(h -> h.getMotivo().contains("Prueba 1") && h.getMotivo().contains("ajuste")));
    }

    @Test
    @DisplayName("Una prueba correcta finaliza el trabajo y queda numerada como prueba 2")
    void registrarPrueba_correcta_finalizaElTrabajo() {
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = ordenEnCurso(ordenId, "EN_PRUEBA");
        prepararPrueba(ordenId, orden, 1);

        OrdenLaboratorioResponseDTO res = laboratorioService.registrarPrueba(ordenId,
                com.odontomax.laboratorio.dto.RegistrarPruebaLaboratorioRequestDTO.builder().resultado("CORRECTO").build(),
                userAdmin);

        assertThat(res.getEstado()).isEqualTo("FINALIZADO");
        assertThat(orden.getFechaInstalacion()).isNotNull();
        verify(pruebaRepository).save(argThat(pr -> pr.getNumero() == 2 && "CORRECTO".equals(pr.getResultado())));
    }

    @Test
    @DisplayName("No se registran pruebas de un trabajo ya finalizado")
    void registrarPrueba_trabajoFinalizado_lanzaConflicto() {
        UUID ordenId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        when(ordenRepository.findByIdForUpdate(ordenId)).thenReturn(Optional.of(ordenEnCurso(ordenId, "FINALIZADO")));

        assertThatThrownBy(() -> laboratorioService.registrarPrueba(ordenId,
                com.odontomax.laboratorio.dto.RegistrarPruebaLaboratorioRequestDTO.builder().resultado("CORRECTO").build(), userAdmin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("en curso");
        verify(pruebaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un pago de laboratorio crea el egreso por método sin aplicar el tope manual")
    void registrarPagoLaboratorio_creaEgresoConMetodoConceptoYReferencia() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        UUID ordenId = UUID.randomUUID();
        UUID pagoId = UUID.randomUUID();
        UUID sesionId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId).codigo("LAB-2026-0099").sedeId("sede-chilca-1")
                .laboratorioId(laboratorioId).costoLaboratorio(new BigDecimal("500.00"))
                .estado("ENVIADO").build();
        when(ordenRepository.findByIdForUpdate(ordenId)).thenReturn(Optional.of(orden));
        when(pagoLaboratorioRepository.findByOrdenIdOrderByPagadoEnAsc(ordenId)).thenReturn(List.of());
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.of(
                LaboratorioExternoEntity.builder().id(laboratorioId).nombre("Lab Huancayo Dental").build()));
        when(pagoLaboratorioRepository.save(any(PagoLaboratorioEntity.class))).thenAnswer(inv -> {
            PagoLaboratorioEntity pago = inv.getArgument(0);
            pago.setId(pagoId);
            return pago;
        });
        when(cajaEgresoModuloApi.registrarEgresoDeSistema(any(), any(), any(), any(), any(), any()))
                .thenReturn(sesionId);

        laboratorioService.registrarPagoLaboratorio(ordenId, RegistrarPagoLaboratorioRequestDTO.builder()
                .monto(new BigDecimal("250.00"))
                .metodoPago("PLIN")
                .numeroOperacion("PLIN-123")
                .build(), userAdmin);

        verify(cajaEgresoModuloApi).registrarEgresoDeSistema(
                "sede-chilca-1", new BigDecimal("250.00"), "PLIN",
                "Laboratorio Lab Huancayo Dental · LAB-2026-0099", "PAGO_LABORATORIO", pagoId);
        verify(pagoLaboratorioRepository, times(2)).save(any(PagoLaboratorioEntity.class));
    }

    @Test
    @DisplayName("Un odontólogo no obtiene campos financieros al consultar el detalle")
    void obtenerOrdenPorId_ocultaFinanzasAOdontologo() {
        UUID ordenId = UUID.randomUUID();
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doctor@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId).pacienteId(pacienteId).especialistaId(especialistaId).laboratorioId(laboratorioId)
                .codigo("LAB-2026-0001").tipoTrabajo("Corona").estado("ENVIADO")
                .costoLaboratorio(new BigDecimal("180.00")).precioPaciente(new BigDecimal("450.00")).build();
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.empty());
        when(especialistaRepository.findById(especialistaId)).thenReturn(Optional.empty());
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.empty());
        when(historialRepository.findByOrdenIdOrderByCreadoEnAsc(ordenId)).thenReturn(List.of());

        var dto = laboratorioService.obtenerOrdenPorId(ordenId, doctor);

        assertThat(dto.getCostoLaboratorio()).isNull();
        assertThat(dto.getPrecioPaciente()).isNull();
        assertThat(dto.getGanancia()).isNull();
        assertThat(dto.getEstadoPago()).isNull();
        assertThat(dto.getMontoPagado()).isNull();
        assertThat(dto.getSaldoLaboratorio()).isNull();
        verifyNoInteractions(pagoLaboratorioRepository);
    }

    @Test
    @DisplayName("Crear orden como odontólogo descarta los importes financieros")
    void crearOrden_odontologoNoPersisteDatosFinancieros() {
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doctor@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.of(
                new PacienteResumen(pacienteId, "44556677", "María", "Flores", null, null, null, null, null, 0)));
        when(especialistaRepository.findById(especialistaId)).thenReturn(Optional.of(
                EspecialistaEntity.builder().id(especialistaId).nombre("Dr. Alex Ríos").build()));
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.of(
                LaboratorioExternoEntity.builder().id(laboratorioId).nombre("Lab Huancayo").tiempoEntregaPromedioDias(4).build()));
        when(ordenRepository.count()).thenReturn(0L);
        when(ordenRepository.findByCodigo(any())).thenReturn(Optional.empty());
        when(ordenRepository.save(any(OrdenLaboratorioEntity.class))).thenAnswer(inv -> {
            OrdenLaboratorioEntity orden = inv.getArgument(0);
            orden.setId(UUID.randomUUID());
            return orden;
        });

        OrdenLaboratorioResponseDTO respuesta = laboratorioService.crearOrden(
                OrdenLaboratorioCreateRequestDTO.builder()
                        .pacienteId(pacienteId)
                        .especialistaId(especialistaId)
                        .sedeId("sede-chilca-1")
                        .laboratorioId(laboratorioId)
                        .tipoTrabajo("Corona")
                        .costoLaboratorio(new BigDecimal("180.00"))
                        .precioPaciente(new BigDecimal("450.00"))
                        .build(),
                doctor);

        assertThat(respuesta.getCostoLaboratorio()).isNull();
        assertThat(respuesta.getPrecioPaciente()).isNull();
        verify(ordenRepository).save(argThat(orden ->
                orden.getCostoLaboratorio() == null && orden.getPrecioPaciente() == null));
        verifyNoInteractions(pagoLaboratorioRepository);
    }

    @Test
    @DisplayName("Editar orden como odontólogo conserva los importes financieros")
    void actualizarOrden_odontologoPreservaDatosFinancieros() {
        ClinicaUserPrincipal doctor = new ClinicaUserPrincipal(
                UUID.randomUUID(), "doctor@odontomax.pe", "Dr. Pérez", "ODONTOLOGO", "sede-chilca-1");
        UUID ordenId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId)
                .codigo("LAB-2026-0001")
                .pacienteId(pacienteId)
                .especialistaId(especialistaId)
                .sedeId("sede-chilca-1")
                .laboratorioId(laboratorioId)
                .tipoTrabajo("Corona")
                .estado("ENVIADO")
                .costoLaboratorio(new BigDecimal("180.00"))
                .precioPaciente(new BigDecimal("450.00"))
                .build();
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.of(
                LaboratorioExternoEntity.builder().id(laboratorioId).nombre("Lab Huancayo").build()));
        when(ordenRepository.save(any(OrdenLaboratorioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pacientesApi.findById(pacienteId)).thenReturn(Optional.empty());
        when(especialistaRepository.findById(especialistaId)).thenReturn(Optional.empty());
        when(historialRepository.findByOrdenIdOrderByCreadoEnAsc(ordenId)).thenReturn(Collections.emptyList());

        OrdenLaboratorioResponseDTO respuesta = laboratorioService.actualizarOrden(
                ordenId,
                OrdenLaboratorioUpdateRequestDTO.builder()
                        .laboratorioId(laboratorioId)
                        .tipoTrabajo("Corona actualizada")
                        .build(),
                doctor);

        assertThat(orden.getCostoLaboratorio()).isEqualByComparingTo("180.00");
        assertThat(orden.getPrecioPaciente()).isEqualByComparingTo("450.00");
        assertThat(respuesta.getCostoLaboratorio()).isNull();
        assertThat(respuesta.getPrecioPaciente()).isNull();
        verifyNoInteractions(pagoLaboratorioRepository);
    }
    @Test
    @DisplayName("Anular pago de laboratorio exitoso revierte en caja 1 vez y audita")
    void anularPagoLaboratorio_exitoso_reversaEnCajaYAuditoria() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        UUID ordenId = UUID.randomUUID();
        UUID pagoId = UUID.randomUUID();
        OrdenLaboratorioEntity orden = OrdenLaboratorioEntity.builder()
                .id(ordenId).codigo("LAB-2026-0099").sedeId("sede-chilca-1")
                .laboratorioId(laboratorioId).costoLaboratorio(new BigDecimal("500.00"))
                .estado("ENVIADO").build();
        LaboratorioExternoEntity lab = LaboratorioExternoEntity.builder()
                .id(laboratorioId).nombre("Lab Huancayo Dental").build();
        PagoLaboratorioEntity pago = PagoLaboratorioEntity.builder()
                .id(pagoId).ordenId(ordenId).laboratorioId(laboratorioId).sedeId("sede-chilca-1")
                .monto(new BigDecimal("200.00")).metodoPago("PLIN").anulado(false).build();

        when(pagoLaboratorioRepository.findById(pagoId)).thenReturn(Optional.of(pago));
        when(laboratorioRepository.findById(laboratorioId)).thenReturn(Optional.of(lab));
        when(ordenRepository.findById(ordenId)).thenReturn(Optional.of(orden));
        when(pagoLaboratorioRepository.save(any(PagoLaboratorioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AnularPagoLaboratorioRequestDTO req = AnularPagoLaboratorioRequestDTO.builder()
                .motivoAnulacion("Digitación errónea").build();

        PagoLaboratorioResponseDTO res = laboratorioService.anularPagoLaboratorio(pagoId, req, userAdmin);

        assertThat(res).isNotNull();
        assertThat(res.getAnulado()).isTrue();
        assertThat(res.getMotivoAnulacion()).isEqualTo("Digitación errónea");
        assertThat(res.getAnuladoPor()).isEqualTo(userAdmin.id());
        verify(cajaEgresoModuloApi, times(1)).revertirEgresoDeSistema(
                eq("sede-chilca-1"), eq(new BigDecimal("200.00")), eq("PLIN"),
                contains("Digitación errónea"), eq("PAGO_LABORATORIO"), eq(pagoId));
        verify(auditoriaRepository).save(argThat(a ->
                "ANULAR_PAGO_LABORATORIO".equals(a.getAccion()) &&
                "pagos_laboratorio".equals(a.getTabla())));
    }

    @Test
    @DisplayName("Anular pago ya anulado lanza PagoLaboratorioNoPermitidoException sin tocar caja")
    void anularPagoLaboratorio_yaAnulado_lanzaException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        UUID pagoId = UUID.randomUUID();
        PagoLaboratorioEntity pago = PagoLaboratorioEntity.builder()
                .id(pagoId).sedeId("sede-chilca-1").anulado(true).build();

        when(pagoLaboratorioRepository.findById(pagoId)).thenReturn(Optional.of(pago));

        AnularPagoLaboratorioRequestDTO req = AnularPagoLaboratorioRequestDTO.builder()
                .motivoAnulacion("Reintento").build();

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pagoId, req, userAdmin))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("ya ha sido anulado previamente");
        verifyNoInteractions(cajaEgresoModuloApi);
    }

    @Test
    @DisplayName("Anular pago sin motivo lanza PagoLaboratorioNoPermitidoException")
    void anularPagoLaboratorio_sinMotivo_lanzaException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userAdmin, null, List.of()));
        UUID pagoId = UUID.randomUUID();
        PagoLaboratorioEntity pago = PagoLaboratorioEntity.builder()
                .id(pagoId).sedeId("sede-chilca-1").anulado(false).build();

        when(pagoLaboratorioRepository.findById(pagoId)).thenReturn(Optional.of(pago));

        AnularPagoLaboratorioRequestDTO req = AnularPagoLaboratorioRequestDTO.builder()
                .motivoAnulacion("   ").build();

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pagoId, req, userAdmin))
                .isInstanceOf(PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("motivo de anulación es obligatorio");
        verifyNoInteractions(cajaEgresoModuloApi);
    }

    @Test
    @DisplayName("Anular pago con rol odontólogo o recepción lanza AccessDeniedException")
    void anularPagoLaboratorio_rolNoPermitido_lanzaAccessDenied() {
        UUID pagoId = UUID.randomUUID();
        ClinicaUserPrincipal recepcionista = new ClinicaUserPrincipal(
                UUID.randomUUID(), "recep@odontomax.pe", "Recepción", "RECEPCION", "sede-chilca-1");

        AnularPagoLaboratorioRequestDTO req = AnularPagoLaboratorioRequestDTO.builder()
                .motivoAnulacion("Motivo").build();

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pagoId, req, recepcionista))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Solo los administradores");
        verifyNoInteractions(pagoLaboratorioRepository);
        verifyNoInteractions(cajaEgresoModuloApi);
    }
}
