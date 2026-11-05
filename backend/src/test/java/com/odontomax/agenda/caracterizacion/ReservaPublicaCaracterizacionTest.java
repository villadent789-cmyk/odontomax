package com.odontomax.agenda.caracterizacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.agenda.application.dto.request.ConsultaReservaRequestDTO;
import com.odontomax.agenda.application.dto.request.ReservaPublicaRequestDTO;
import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.infrastructure.adapter.InMemoryRateLimitAdapter;
import com.odontomax.agenda.infrastructure.web.ReservaPublicaController;
import com.odontomax.identidad.testsupport.UsuariosSimuladosWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.infrastructure.security.TestSecurityEnvironmentInitializer;
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

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReservaPublicaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, UsuariosSimuladosWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, InMemoryRateLimitAdapter.class})
@ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class ReservaPublicaCaracterizacionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrarReservaPublicaUseCase registrarReservaPublicaUseCase;

    @MockBean
    private ConsultarReservaPublicaUseCase consultarReservaPublicaUseCase;

    private ReservaPublicaRequestDTO crearRequest(String dni, String nombres, String apellidos, String telefono, String sedeId, LocalDate fecha, String turno, String motivo) {
        ReservaPublicaRequestDTO dto = new ReservaPublicaRequestDTO();
        dto.setDni(dni);
        dto.setNombres(nombres);
        dto.setApellidos(apellidos);
        dto.setTelefono(telefono);
        dto.setSedeId(sedeId);
        dto.setFechaPreferencia(fecha);
        dto.setTurno(turno);
        dto.setMotivoConsulta(motivo);
        return dto;
    }

    @Test
    @DisplayName("POST /reservas-publicas público retorna 201 con ticket formato OM-XXXXXXXX y campos completos")
    void registrarReserva_201() throws Exception {
        ReservaPublicaRequestDTO request = crearRequest(
                "44556677", "Rosa", "Flores", "987654321", "sede-chilca-1",
                LocalDate.now().plusDays(2), "MANANA", "Ortodoncia preventiva"
        );

        TicketReservaDTO ticket = TicketReservaDTO.builder()
                .codigoTicket("OM-7K9M2P4X")
                .sedeId("sede-chilca-1")
                .sedeNombre("sede-chilca-1")
                .fecha(request.getFechaPreferencia())
                .turno("MANANA")
                .estado("PENDIENTE_WEB")
                .especialidad("Ortodoncia preventiva")
                .mensaje("¡Reserva registrada con éxito!")
                .build();

        when(registrarReservaPublicaUseCase.registrarReservaPublica(any())).thenReturn(ticket);

        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr("10.1.1.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoTicket", matchesPattern("^OM-[2-9A-HJ-NP-Z]{8}$")))
                .andExpect(jsonPath("$.sedeId").value("sede-chilca-1"))
                .andExpect(jsonPath("$.turno").value("MANANA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_WEB"))
                .andExpect(jsonPath("$.especialidad").value("Ortodoncia preventiva"));
    }

    @Test
    @DisplayName("POST /reservas-publicas con fecha en el pasado lanza 400 Bad Request")
    void registrarReserva_fechaPasada_400() throws Exception {
        ReservaPublicaRequestDTO request = crearRequest(
                "44556678", "Rosa", "Flores", "987654322", "sede-chilca-1",
                LocalDate.now().minusDays(1), "MANANA", "Limpieza"
        );

        when(registrarReservaPublicaUseCase.registrarReservaPublica(any()))
                .thenThrow(new IllegalArgumentException("La fecha de reserva no puede ser en el pasado."));

        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr("10.1.1.2"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("La fecha de reserva no puede ser en el pasado."));
    }

    @Test
    @DisplayName("POST /reservas-publicas supera límite de 5 intentos y responde 429 Too Many Requests")
    void registrarReserva_rateLimit_429() throws Exception {
        String testIp = "10.1.1.3";
        ReservaPublicaRequestDTO request = crearRequest(
                "11223344", "Carlos", "Pérez", "911222333", "sede-chilca-1",
                LocalDate.now().plusDays(3), "TARDE", "Revisión"
        );

        TicketReservaDTO ticket = TicketReservaDTO.builder()
                .codigoTicket("OM-ABC12345")
                .sedeId("sede-chilca-1")
                .build();

        when(registrarReservaPublicaUseCase.registrarReservaPublica(any())).thenReturn(ticket);

        // Primeras 5 solicitudes permitidas
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/reservas-publicas")
                            .with(req -> { req.setRemoteAddr(testIp); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // Intento 6 debe retornar 429
        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje").value("Ha superado el límite de intentos de reserva. Por favor espere unos minutos o contáctenos por WhatsApp."));
    }

    @Test
    @DisplayName("POST /reservas-publicas/consulta con ticket y DNI coincidentes retorna 200 OK")
    void consultarReserva_200() throws Exception {
        ConsultaReservaRequestDTO request = new ConsultaReservaRequestDTO("OM-7K9M2P4X", "44556677");

        ConsultaCitaResponseDTO response = ConsultaCitaResponseDTO.builder()
                .ticket("OM-7K9M2P4X")
                .estado("PENDIENTE_WEB")
                .fecha(LocalDate.of(2026, 10, 20))
                .turno("MANANA")
                .sedeId("sede-chilca-1")
                .sedeNombre("sede-chilca-1")
                .build();

        when(consultarReservaPublicaUseCase.consultarReservaPublica("OM-7K9M2P4X", "44556677")).thenReturn(response);

        mockMvc.perform(post("/reservas-publicas/consulta")
                        .with(req -> { req.setRemoteAddr("10.1.1.4"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("OM-7K9M2P4X"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_WEB"))
                .andExpect(jsonPath("$.turno").value("MANANA"))
                .andExpect(jsonPath("$.sedeId").value("sede-chilca-1"));
    }

    @Test
    @DisplayName("POST /reservas-publicas/consulta con ticket o DNI no coincidentes retorna 400 Bad Request")
    void consultarReserva_noCoincide_400() throws Exception {
        ConsultaReservaRequestDTO request = new ConsultaReservaRequestDTO("OM-INVALID", "00000000");

        when(consultarReservaPublicaUseCase.consultarReservaPublica("OM-INVALID", "00000000"))
                .thenThrow(new IllegalArgumentException("No se encontró ninguna reserva activa con el ticket y DNI proporcionados."));

        mockMvc.perform(post("/reservas-publicas/consulta")
                        .with(req -> { req.setRemoteAddr("10.1.1.5"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("No se encontró ninguna reserva activa con el ticket y DNI proporcionados."));
    }

    @Test
    @DisplayName("POST /reservas-publicas/consulta supera límite de 10 intentos y retorna 429 Too Many Requests")
    void consultarReserva_rateLimit_429() throws Exception {
        String testIp = "10.1.1.6";
        ConsultaReservaRequestDTO request = new ConsultaReservaRequestDTO("OM-RATELIM1", "12345678");

        ConsultaCitaResponseDTO response = ConsultaCitaResponseDTO.builder()
                .ticket("OM-RATELIM1")
                .estado("PROGRAMADA")
                .build();

        when(consultarReservaPublicaUseCase.consultarReservaPublica(any(), any())).thenReturn(response);

        // Primeras 10 consultas permitidas
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/reservas-publicas/consulta")
                            .with(req -> { req.setRemoteAddr(testIp); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // Intento 11 debe retornar 429
        mockMvc.perform(post("/reservas-publicas/consulta")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.mensaje").value("Ha superado el límite de consultas permitidas. Intente más tarde."));
    }

    @Test
    @DisplayName("POST /reservas-publicas con el campo trampa (honeypot) lleno responde 400 y no registra nada")
    void registrarReserva_honeypot_400() throws Exception {
        ReservaPublicaRequestDTO request = crearRequest(
                "55667788", "Bot", "Spam", "955666777", "sede-chilca-1",
                LocalDate.now().plusDays(2), "MANANA", "Revisión"
        );
        request.setSitioWeb("http://spam.example");

        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr("10.9.9.9"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        org.mockito.Mockito.verifyNoInteractions(registrarReservaPublicaUseCase);
    }

    @Test
    @DisplayName("Las respuestas de la API llevan las cabeceras de seguridad (sin incrustar, sin referrer, sin permisos de dispositivo)")
    void cabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/reservas-publicas/catalogo/no-existe"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Referrer-Policy", "no-referrer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy", "frame-ancestors 'none'"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"));
    }
}
