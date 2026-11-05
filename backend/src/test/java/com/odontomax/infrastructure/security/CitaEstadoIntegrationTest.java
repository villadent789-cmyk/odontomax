package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.*;
import com.odontomax.agenda.infrastructure.web.CitaController;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CitaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class CitaEstadoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private com.odontomax.agenda.application.port.in.RegistrarAdelantoUseCase registrarAdelantoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.RegistrarContactoUseCase registrarContactoUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ConsultarBandejaTareasUseCase consultarBandejaTareasUseCase;

    @MockBean
    private com.odontomax.agenda.application.port.in.ProcesarExpiracionUseCase procesarExpiracionUseCase;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    private String tokenChilca1;
    private final UUID doctorId = UUID.randomUUID();
    private final String emailDoctor = "doctor.chilca1@odontomax.pe";
    private final UUID citaId = UUID.randomUUID();

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
    @DisplayName("Cambiar estado a ATENDIDA enviando body JSON {estado, motivo} con seguridad real retorna 200")
    void testCambiarEstadoAtendidaConMotivo() throws Exception {
        CitaResponseDTO citaOriginal = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-chilca-1")
                .estado("PROGRAMADA")
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Limpieza dental")
                .build();

        CitaResponseDTO citaActualizada = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-chilca-1")
                .estado("ATENDIDA")
                .motivoCancelacion("Tratamiento finalizado exitosamente")
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Limpieza dental")
                .notas("[Estado: ATENDIDA] Motivo: Tratamiento finalizado exitosamente")
                .build();

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(citaOriginal);
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("ATENDIDA"), eq("Tratamiento finalizado exitosamente")))
                .thenReturn(citaActualizada);

        String jsonPayload = """
                {
                    "estado": "ATENDIDA",
                    "motivo": "Tratamiento finalizado exitosamente"
                }
                """;

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId.toString()))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Tratamiento finalizado exitosamente"));
    }

    @Test
    @DisplayName("Cambiar estado a CANCELADA enviando body JSON {estado, motivo} guarda motivo y retorna 200")
    void testCambiarEstadoCanceladaConMotivo() throws Exception {
        CitaResponseDTO citaOriginal = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-chilca-1")
                .estado("PROGRAMADA")
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Consulta general")
                .build();

        CitaResponseDTO citaCancelada = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-chilca-1")
                .estado("CANCELADA")
                .motivoCancelacion("Paciente no se presentó")
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Consulta general")
                .build();

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(citaOriginal);
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CANCELADA"), eq("Paciente no se presentó")))
                .thenReturn(citaCancelada);

        String jsonPayload = """
                {
                    "estado": "CANCELADA",
                    "motivo": "Paciente no se presentó"
                }
                """;

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(citaId.toString()))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("Paciente no se presentó"));
    }

    @Test
    @DisplayName("Cambiar estado con estado inválido retorna 400 Bad Request")
    void testCambiarEstadoInvalido() throws Exception {
        String jsonPayload = """
                {
                    "estado": "ESTADO_INVENTADO",
                    "motivo": "Prueba error"
                }
                """;

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cambiar estado en cita de otra sede retorna 403 Forbidden")
    void testCambiarEstadoSedeAjena() throws Exception {
        CitaResponseDTO citaOtraSede = CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-eltambo")
                .estado("PROGRAMADA")
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Consulta")
                .build();

        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(citaOtraSede);

        String jsonPayload = """
                {
                    "estado": "CONFIRMADA",
                    "motivo": "Confirmación telefónica"
                }
                """;

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .header("Authorization", "Bearer " + tokenChilca1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cambiar estado sin autenticación retorna 401 Unauthorized")
    void testCambiarEstadoSinAuth() throws Exception {
        String jsonPayload = """
                {
                    "estado": "ATENDIDA"
                }
                """;

        mockMvc.perform(patch("/citas/" + citaId + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }
}
