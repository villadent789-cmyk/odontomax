package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.agenda.application.dto.request.CitaCancelarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaReprogramarRequestDTO;
import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.domain.exception.CitaColisionException;
import com.odontomax.agenda.infrastructure.web.CitaController;
import com.odontomax.identidad.testsupport.UsuariosSimuladosWebMvcConfig;
import com.odontomax.identidad.testsupport.UsuarioTestFixtures;
import org.springframework.context.ApplicationContext;
import com.odontomax.identidad.infrastructure.security.*;
import com.odontomax.infrastructure.security.TestSecurityEnvironmentInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CitaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, UsuariosSimuladosWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class CitaEndpointsCaracterizacionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    private RegistrarAdelantoUseCase registrarAdelantoUseCase;

    @MockBean
    private RegistrarContactoUseCase registrarContactoUseCase;

    @MockBean
    private ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;

    @MockBean
    private ProcesarExpiracionUseCase procesarExpiracionUseCase;

    @Autowired
    private ApplicationContext context;

    private String adminToken;
    private final UUID adminId = UUID.randomUUID();
    private final UUID citaId = UUID.randomUUID();
    private final UUID pacienteId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UsuarioTestFixtures.simularUsuarioActivo(context, adminId, "admin@odontomax.pe", "Admin General",
                "ADMIN_GENERAL", null);

        adminToken = jwtTokenProvider.generateToken(adminId, "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null, 0);
    }

    private CitaResponseDTO sampleCitaResponse() {
        return CitaResponseDTO.builder()
                .id(citaId)
                .pacienteId(pacienteId)
                .pacienteNombre("Juan Perez")
                .pacienteTelefono("999888777")
                .doctorId(doctorId)
                .doctorNombre("Dr. Carlos Ruiz")
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 15))
                .hora(LocalTime.of(10, 30))
                .duracionMinutos(30)
                .motivoConsulta("Evaluación general")
                .estado("PROGRAMADA")
                .codigoTicket("OM-TEST1234")
                .sillonNumero(1)
                .build();
    }

    @Test
    @DisplayName("GET /citas retorna 200 y array de CitaResponseDTO con formato JSON esperado")
    void listarCitas_200() throws Exception {
        when(consultarCitaUseCase.obtenerCitasDelDia(any(), any())).thenReturn(List.of(sampleCitaResponse()));

        mockMvc.perform(get("/citas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("fecha", "2026-10-15")
                        .param("sedeId", "sede-chilca-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(citaId.toString()))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteId.toString()))
                .andExpect(jsonPath("$[0].pacienteNombre").value("Juan Perez"))
                .andExpect(jsonPath("$[0].pacienteTelefono").value("999888777"))
                .andExpect(jsonPath("$[0].doctorId").value(doctorId.toString()))
                .andExpect(jsonPath("$[0].doctorNombre").value("Dr. Carlos Ruiz"))
                .andExpect(jsonPath("$[0].sedeId").value("sede-chilca-1"))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-15"))
                .andExpect(jsonPath("$[0].hora").value("10:30:00"))
                .andExpect(jsonPath("$[0].duracionMinutos").value(30))
                .andExpect(jsonPath("$[0].estado").value("PROGRAMADA"))
                .andExpect(jsonPath("$[0].codigoTicket").value("OM-TEST1234"));
    }

    @Test
    @DisplayName("GET /citas con rango desde/hasta retorna 200")
    void listarCitas_rango_200() throws Exception {
        when(consultarCitaUseCase.obtenerCitasPorRango(any(), any(), any())).thenReturn(List.of(sampleCitaResponse()));

        mockMvc.perform(get("/citas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(citaId.toString()));
    }

    @Test
    @DisplayName("GET /citas/reservas-web-pendientes retorna 200 y lista")
    void listarReservasWebPendientes_200() throws Exception {
        CitaResponseDTO pendiente = sampleCitaResponse();
        pendiente.setEstado("PENDIENTE_WEB");
        when(consultarCitaUseCase.obtenerReservasWebPendientes(any())).thenReturn(List.of(pendiente));

        mockMvc.perform(get("/citas/reservas-web-pendientes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE_WEB"));
    }

    @Test
    @DisplayName("GET /citas/lista-espera retorna 200 y citas en estado EN_SALA")
    void listarListaEspera_200() throws Exception {
        CitaResponseDTO enSala = sampleCitaResponse();
        enSala.setEstado("EN_SALA");
        when(consultarCitaUseCase.obtenerListaEspera(any())).thenReturn(List.of(enSala));

        mockMvc.perform(get("/citas/lista-espera")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("EN_SALA"));
    }

    @Test
    @DisplayName("POST /citas con payload válido retorna 201 Created y cuerpo de CitaResponseDTO")
    void agendarCita_201() throws Exception {
        CitaRequestDTO request = CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .pacienteNombre("Juan Perez")
                .pacienteTelefono("999888777")
                .doctorId(doctorId)
                .doctorNombre("Dr. Carlos Ruiz")
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 15))
                .hora(LocalTime.of(10, 30))
                .duracionMinutos(30)
                .motivoConsulta("Evaluación general")
                .sillonNumero(1)
                .build();

        when(agendarCitaUseCase.agendarCita(any(CitaRequestDTO.class), any())).thenReturn(sampleCitaResponse());

        mockMvc.perform(post("/citas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(citaId.toString()))
                .andExpect(jsonPath("$.estado").value("PROGRAMADA"));
    }

    @Test
    @DisplayName("POST /citas con colisión de doctor retorna 409 Conflict con estructura de error estándar")
    void agendarCita_colision_409() throws Exception {
        CitaRequestDTO request = CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId("sede-chilca-1")
                .fecha(LocalDate.of(2026, 10, 15))
                .hora(LocalTime.of(10, 30))
                .motivoConsulta("Evaluación general")
                .build();

        when(agendarCitaUseCase.agendarCita(any(CitaRequestDTO.class), any()))
                .thenThrow(new CitaColisionException("El doctor ya tiene una cita agendada en esa fecha y hora."));

        mockMvc.perform(post("/citas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("El doctor ya tiene una cita agendada en esa fecha y hora."))
                .andExpect(jsonPath("$.mensaje").value("El doctor ya tiene una cita agendada en esa fecha y hora."));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/estado con transición válida retorna 200")
    void cambiarEstado_200() throws Exception {
        CitaResponseDTO existente = sampleCitaResponse();
        CitaResponseDTO atendida = sampleCitaResponse();
        atendida.setEstado("ATENDIDA");

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(existente);
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("ATENDIDA"), any())).thenReturn(atendida);

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "ATENDIDA", "motivo", "Consulta finalizada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/estado con estado inválido retorna 400")
    void cambiarEstado_invalido_400() throws Exception {
        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "ESTADO_INEXISTENTE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/reprogramar con datos válidos retorna 200")
    void reprogramarCita_200() throws Exception {
        CitaReprogramarRequestDTO dto = new CitaReprogramarRequestDTO(
                LocalDate.of(2026, 10, 20),
                LocalTime.of(11, 0),
                "Paciente solicita cambio de fecha"
        );

        CitaResponseDTO reprogramada = sampleCitaResponse();
        reprogramada.setFecha(LocalDate.of(2026, 10, 20));
        reprogramada.setHora(LocalTime.of(11, 0));

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(sampleCitaResponse());
        when(reprogramarCitaUseCase.reprogramarCita(eq(citaId), any())).thenReturn(reprogramada);

        mockMvc.perform(patch("/citas/" + citaId + "/reprogramar")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-10-20"))
                .andExpect(jsonPath("$.hora").value("11:00:00"));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/cancelar con motivo válido retorna 200")
    void cancelarCita_200() throws Exception {
        CitaCancelarRequestDTO dto = new CitaCancelarRequestDTO("Paciente no podrá asistir por viaje");
        CitaResponseDTO cancelada = sampleCitaResponse();
        cancelada.setEstado("CANCELADA");
        cancelada.setMotivoCancelacion("Paciente no podrá asistir por viaje");

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(sampleCitaResponse());
        when(cancelarCitaUseCase.cancelarCita(eq(citaId), any())).thenReturn(cancelada);

        mockMvc.perform(patch("/citas/" + citaId + "/cancelar")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Paciente no podrá asistir por viaje"));
    }
}
