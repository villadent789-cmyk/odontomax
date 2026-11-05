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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /citas/{id}/estado con CONFIRMADA: roles permitidos, normalización del estado,
 * 400 para estados inválidos o transiciones bloqueadas y cancelación sin motivo.
 */
@WebMvcTest(controllers = {CitaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class CitaConfirmacionSecurityTest {

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

    private final UUID citaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(cita("PROGRAMADA"));
    }

    private CitaResponseDTO cita(String estado) {
        return CitaResponseDTO.builder()
                .id(citaId)
                .sedeId("sede-chilca-1")
                .estado(estado)
                .fecha(LocalDate.now())
                .hora(LocalTime.of(10, 0))
                .motivoConsulta("Limpieza dental")
                .build();
    }

    /** Token de un usuario activo cuyo rol y sede se leen de la BD (mock), como hace JwtAuthenticationFilter. */
    private String tokenDe(String rol, String sedeId) {
        UUID id = UUID.randomUUID();
        String email = rol.toLowerCase() + "." + id + "@odontomax.pe";
        when(usuarioRepository.findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(id).email(email).nombre("Usuario " + rol).rol(rol).sedeId(sedeId).activo(true)
                        .build()));
        return jwtTokenProvider.generateToken(id, email, "Usuario " + rol, rol, sedeId, 0);
    }

    private org.springframework.test.web.servlet.ResultActions patchEstado(String token, String body) throws Exception {
        return mockMvc.perform(patch("/citas/" + citaId + "/estado")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @ParameterizedTest(name = "rol {0} puede confirmar")
    @ValueSource(strings = {"ADMIN_GENERAL", "ADMIN_SEDE", "ODONTOLOGO", "RECEPCION"})
    @DisplayName("Los cuatro roles clínicos pueden confirmar una cita de su sede (200)")
    void rolesPermitidosConfirman(String rol) throws Exception {
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CONFIRMADA"), any())).thenReturn(cita("CONFIRMADA"));

        patchEstado(tokenDe(rol, "sede-chilca-1"), "{\"estado\":\"CONFIRMADA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("ADMIN_GENERAL sin sede asignada confirma citas de cualquier sede (200)")
    void adminGeneralSinSedeConfirma() throws Exception {
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CONFIRMADA"), any())).thenReturn(cita("CONFIRMADA"));

        patchEstado(tokenDe("ADMIN_GENERAL", null), "{\"estado\":\"CONFIRMADA\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un rol fuera de la lista (p. ej. PACIENTE) recibe 403 y no cambia el estado")
    void rolNoPermitidoRecibe403() throws Exception {
        patchEstado(tokenDe("PACIENTE", "sede-chilca-1"), "{\"estado\":\"CONFIRMADA\"}")
                .andExpect(status().isForbidden());
        verify(cambiarEstadoCitaUseCase, never()).cambiarEstado(any(), any(), any());
    }

    @Test
    @DisplayName("El estado se normaliza: ' confirmada ' llega al servicio como CONFIRMADA")
    void estadoSeNormaliza() throws Exception {
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CONFIRMADA"), any())).thenReturn(cita("CONFIRMADA"));

        patchEstado(tokenDe("RECEPCION", "sede-chilca-1"), "{\"estado\":\" confirmada \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @ParameterizedTest(name = "body {0} → 400")
    @ValueSource(strings = {"{}", "{\"estado\":\"\"}", "{\"estado\":\"CONFIRMADO\"}", "{\"estado\":\"FALTO\"}"})
    @DisplayName("Estado ausente, vacío o fuera de la lista devuelve 400 sin tocar la cita")
    void estadoInvalidoDevuelve400(String body) throws Exception {
        patchEstado(tokenDe("RECEPCION", "sede-chilca-1"), body)
                .andExpect(status().isBadRequest());
        verify(cambiarEstadoCitaUseCase, never()).cambiarEstado(any(), any(), any());
    }

    @Test
    @DisplayName("Cancelar por /estado sin motivo devuelve 400 (el servicio lo rechaza)")
    void cancelarSinMotivoDevuelve400() throws Exception {
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CANCELADA"), any()))
                .thenThrow(new IllegalArgumentException("Debe indicar el motivo de la cancelación (mínimo 5 caracteres)."));

        patchEstado(tokenDe("RECEPCION", "sede-chilca-1"), "{\"estado\":\"CANCELADA\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe indicar el motivo de la cancelación (mínimo 5 caracteres)."));
    }

    @Test
    @DisplayName("PATCH /citas/{id}/cancelar sin body devuelve 400 (motivo obligatorio)")
    void cancelarEndpointSinBodyDevuelve400() throws Exception {
        when(cancelarCitaUseCase.cancelarCita(eq(citaId), any()))
                .thenThrow(new IllegalArgumentException("Debe indicar el motivo de la cancelación (mínimo 5 caracteres)."));

        mockMvc.perform(patch("/citas/" + citaId + "/cancelar")
                        .header("Authorization", "Bearer " + tokenDe("RECEPCION", "sede-chilca-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Transición bloqueada (ATENDIDA → CANCELADA) devuelve 400, no 500")
    void transicionBloqueadaDevuelve400() throws Exception {
        when(consultarCitaUseCase.obtenerPorId(citaId)).thenReturn(cita("ATENDIDA"));
        when(cambiarEstadoCitaUseCase.cambiarEstado(eq(citaId), eq("CANCELADA"), eq("Paciente pidió anular")))
                .thenThrow(new IllegalArgumentException("No se puede cancelar una cita que ya fue atendida."));

        patchEstado(tokenDe("ADMIN_SEDE", "sede-chilca-1"), "{\"estado\":\"CANCELADA\",\"motivo\":\"Paciente pidió anular\"}")
                .andExpect(status().isBadRequest());
    }
}
