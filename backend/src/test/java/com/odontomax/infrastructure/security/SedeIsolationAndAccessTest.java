package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.infrastructure.web.CitaController;
import com.odontomax.application.service.RadiografiaService;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.pacientes.infrastructure.web.PacienteController;
import com.odontomax.presentation.controller.RadiografiaController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PacienteController.class, CitaController.class, RadiografiaController.class})
@Import({com.odontomax.pacientes.application.service.PacienteApplicationService.class, SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class SedeIsolationAndAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.odontomax.pacientes.application.port.in.GestionarSolicitudesCorreccionUseCase solicitudesCorreccionUseCase;
    @MockBean
    private com.odontomax.pacientes.application.port.out.ExpedienteDependenciasPort expedienteDependenciasPort;
    @MockBean
    private com.odontomax.pacientes.application.port.out.BorradoArchivosPort borradoArchivosPort;
    @MockBean
    private com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort registroAuditoriaPort;
    @MockBean
    private com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository pacienteRepository;
    @MockBean
    private com.odontomax.pacientes.application.port.out.HistorialCitasPort historialCitasPort;

    @MockBean
    private com.odontomax.pacientes.api.ConsultaPacientesModuloApi consultaPacientesModuloApi;

    @MockBean
    private com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase exportacionPacienteService;

    @MockBean
    private AgendarCitaUseCase agendarCitaUseCase;

    @MockBean
    private ReprogramarCitaUseCase reprogramarCitaUseCase;

    @MockBean
    private CancelarCitaUseCase cancelarCitaUseCase;

    @MockBean
    private CambiarEstadoCitaUseCase cambiarEstadoCitaUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.IniciarAtencionCitaUseCase iniciarAtencionCitaUseCase;

    @MockBean
    private ConsultarCitaUseCase consultarCitaUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.RegistrarAdelantoUseCase registrarAdelantoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.RegistrarContactoUseCase registrarContactoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ProcesarExpiracionUseCase procesarExpiracionUseCase;

    @MockBean
    private RadiografiaService radiografiaService;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenChilca1;
    private final UUID doctorId = UUID.randomUUID();
    private final String emailDoctor = "doctor.chilca1@odontomax.pe";

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findByEmailIgnoreCase(emailDoctor))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(doctorId)
                        .email(emailDoctor)
                        .nombre("Dr. Carlos Chilca 1")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .activo(true)
                        .build()));

        tokenChilca1 = jwtTokenProvider.generateToken(
                doctorId,
                emailDoctor,
                "Dr. Carlos Chilca 1",
                "ODONTOLOGO",
                "sede-chilca-1"
        , 0);
    }

    @Test
    @DisplayName("(a.1) Pacientes: Usuario de sede-chilca-1 debe recibir 403 al solicitar pacientes de sede-eltambo")
    void testAislamientoPacientesOtraSede() throws Exception {
        mockMvc.perform(get("/pacientes")
                        .param("sedeId", "sede-eltambo")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("(a.2) Citas: Usuario de sede-chilca-1 debe recibir 403 al solicitar citas de sede-eltambo")
    void testAislamientoCitasOtraSede() throws Exception {
        mockMvc.perform(get("/citas")
                        .param("sedeId", "sede-eltambo")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("(a.3) Radiografías: Usuario de sede-chilca-1 debe recibir 403 al solicitar radiografías de paciente en sede-eltambo")
    void testAislamientoRadiografiasOtraSede() throws Exception {
        UUID pacienteSede2Id = UUID.randomUUID();
        when(radiografiaService.listarPorPaciente(pacienteSede2Id))
                .thenThrow(new AccessDeniedException("Acceso denegado: no tiene permisos para consultar estudios de un paciente registrado en otra sede"));

        mockMvc.perform(get("/radiografias/paciente/" + pacienteSede2Id)
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("(a.2b) Agenda por rango: sigue aislada por sede (403 a otra sede) y usa el rango en la propia")
    void testAgendaPorRangoRespetaSede() throws Exception {
        mockMvc.perform(get("/citas")
                        .param("desde", "2026-10-01").param("hasta", "2026-12-31")
                        .param("sedeId", "sede-eltambo")
                        .header("Authorization", "Bearer " + tokenChilca1))
                .andExpect(status().isForbidden());

        when(consultarCitaUseCase.obtenerCitasPorRango(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 12, 31), "sede-chilca-1"))
                .thenReturn(java.util.List.of());
        mockMvc.perform(get("/citas")
                        .param("desde", "2026-10-01").param("hasta", "2026-12-31")
                        .header("Authorization", "Bearer " + tokenChilca1))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(consultarCitaUseCase).obtenerCitasPorRango(
                java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 12, 31), "sede-chilca-1");
    }
}
