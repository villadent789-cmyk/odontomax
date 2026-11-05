package com.odontomax.infrastructure.security;

import com.odontomax.identidad.testsupport.IdentidadSeguridadWebMvcConfig;
import com.odontomax.identidad.infrastructure.security.SecurityConfig;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationFilter;
import com.odontomax.identidad.infrastructure.security.JwtAuthenticationEntryPoint;
import com.odontomax.identidad.infrastructure.security.CustomAccessDeniedHandler;
import com.odontomax.identidad.infrastructure.security.JwtTokenProvider;
import com.odontomax.identidad.infrastructure.security.TotpService;
import com.odontomax.agenda.application.dto.response.ConsultaCitaResponseDTO;
import com.odontomax.agenda.application.dto.response.TicketReservaDTO;
import com.odontomax.agenda.application.port.in.ConsultarReservaPublicaUseCase;
import com.odontomax.agenda.application.port.in.RegistrarReservaPublicaUseCase;
import com.odontomax.agenda.infrastructure.adapter.InMemoryRateLimitAdapter;
import com.odontomax.agenda.infrastructure.web.ReservaPublicaController;
import com.odontomax.application.dto.request.LoginRequestDTO;
import com.odontomax.identidad.application.service.LoginService;
import com.odontomax.identidad.infrastructure.adapter.InMemoryBruteForceAdapter;
import com.odontomax.identidad.infrastructure.adapter.InMemoryLogin2faPendienteAdapter;
import com.odontomax.identidad.infrastructure.adapter.InMemoryTotpPasoUsadoAdapter;
import com.odontomax.identidad.infrastructure.persistence.adapter.UsuarioJpaAdapter;
import com.odontomax.identidad.infrastructure.persistence.mapper.UsuarioMapperImpl;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReservaPublicaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, IdentidadSeguridadWebMvcConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, InMemoryRateLimitAdapter.class})
@org.springframework.test.context.ContextConfiguration(initializers = TestSecurityEnvironmentInitializer.class)
class PublicEndpointsAndBruteforceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrarReservaPublicaUseCase registrarReservaPublicaUseCase;

    @MockBean
    private ConsultarReservaPublicaUseCase consultarReservaPublicaUseCase;

    @MockBean
    private SpringDataUsuarioRepository usuarioRepository;

    @Test
    @DisplayName("(b) Login: Falla con contraseña incorrecta y bloquea la cuenta tras 5 intentos consecutivos (Email + IP)")
    void testBloqueoTrasCincoIntentosFallidos() {
        SpringDataUsuarioRepository mockRepo = mock(SpringDataUsuarioRepository.class);
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider mockJwt = mock(JwtTokenProvider.class);

        String testEmail = "usuario.prueba." + UUID.randomUUID() + "@odontomax.pe";
        String testIp = "192.168.10.55";

        when(mockRepo.findByEmailIgnoreCase(testEmail)).thenReturn(Optional.of(
                UsuarioEntity.builder()
                        .id(UUID.randomUUID())
                        .email(testEmail)
                        .nombre("Usuario Test")
                        .rol("ODONTOLOGO")
                        .sedeId("sede-chilca-1")
                        .passwordHash("$2a$10$hashSimuladoConBcryptQueNoCoincide")
                        .activo(true)
                        .build()
        ));
        when(mockEncoder.matches(eq("ClaveErronea"), any())).thenReturn(false);

        LoginService service = new LoginService(new UsuarioJpaAdapter(mockRepo, new UsuarioMapperImpl()),
                mockJwt, mockEncoder, new InMemoryBruteForceAdapter(), new TotpService(),
                new InMemoryLogin2faPendienteAdapter(), new InMemoryTotpPasoUsadoAdapter());

        LoginRequestDTO req = new LoginRequestDTO(testEmail, "ClaveErronea");

        // Intentos 1 a 5: Fallan con BadCredentialsException genérica
        for (int i = 1; i <= 5; i++) {
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    service.login(req, testIp)
            );
            assertTrue(ex.getMessage().contains("Credenciales inválidas"));
        }

        // Intento 6: Debe estar activado el bloqueo temporal anti-fuerza bruta
        BadCredentialsException blockedEx = assertThrows(BadCredentialsException.class, () ->
                service.login(req, testIp)
        );
        assertTrue(blockedEx.getMessage().contains("Demasiados intentos fallidos")
                && blockedEx.getMessage().contains("bloqueada"));
    }

    @Test
    @DisplayName("(c.1) Reserva pública: Valida que la sede sea válida (ej. rechaza sede inexistente)")
    void testReservaPublicaSedeInvalida() throws Exception {
        when(registrarReservaPublicaUseCase.registrarReservaPublica(any()))
                .thenThrow(new IllegalArgumentException("Sede inválida. Sedes operativas: sede-chilca-1, sede-eltambo, sede-villamax-chilca"));

        String payload = """
                {
                    "nombres": "Rosa Maria",
                    "apellidos": "Quispe Gomez",
                    "dni": "72345678",
                    "telefono": "+51987654321",
                    "sedeId": "sede-no-existe",
                    "fecha": "2026-10-15",
                    "turno": "TARDE",
                    "motivo": "Consulta general"
                }
                """;

        mockMvc.perform(post("/reservas-publicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Sede inválida")));
    }

    @Test
    @DisplayName("(c.2) Reserva pública: Rechaza fechas en el pasado (conforme a zona horaria America/Lima)")
    void testReservaPublicaFechaPasada() throws Exception {
        when(registrarReservaPublicaUseCase.registrarReservaPublica(any()))
                .thenThrow(new IllegalArgumentException("La fecha de reserva no puede ser anterior a hoy en zona horaria America/Lima"));

        String payload = """
                {
                    "nombres": "Carlos",
                    "apellidos": "Vargas",
                    "dni": "72345678",
                    "telefono": "+51987654321",
                    "sedeId": "sede-chilca-1",
                    "fecha": "2020-01-01",
                    "turno": "MANANA",
                    "motivo": "Consulta"
                }
                """;

        mockMvc.perform(post("/reservas-publicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("anterior a hoy")));
    }

    @Test
    @DisplayName("(c.3) Reserva pública: Respuesta NO filtra nombre ni teléfono de paciente (Ley 29733)")
    void testReservaPublicaNoFiltraDatosPersonales() throws Exception {
        TicketReservaDTO ticket = TicketReservaDTO.builder()
                .codigoTicket("OM-A1B2C3D4")
                .sedeId("sede-chilca-1")
                .sedeNombre("Chilca I (Principal)")
                .fecha(LocalDate.now().plusDays(2))
                .turno("MANANA")
                .estado("PENDIENTE_WEB")
                .especialidad("Odontología General")
                .mensaje("Su solicitud de cita ha sido recibida exitosamente")
                .build();

        when(registrarReservaPublicaUseCase.registrarReservaPublica(any())).thenReturn(ticket);

        String payload = """
                {
                    "nombres": "Juan",
                    "apellidos": "Perez",
                    "dni": "45678912",
                    "telefono": "+51999888777",
                    "sedeId": "sede-chilca-1",
                    "fecha": "2026-10-20",
                    "turno": "MANANA",
                    "motivo": "Limpieza"
                }
                """;

        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr("10.88.88.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoTicket").value("OM-A1B2C3D4"))
                .andExpect(jsonPath("$.sedeId").value("sede-chilca-1"))
                .andExpect(jsonPath("$.pacienteNombre").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist());
    }

    @Test
    @DisplayName("(c.4) Reserva pública: Retorna 429 cuando una misma IP excede el límite permitido")
    void testReservaPublicaRateLimit() throws Exception {
        TicketReservaDTO ticket = TicketReservaDTO.builder()
                .codigoTicket("OM-RATE9999")
                .sedeId("sede-chilca-1")
                .sedeNombre("Chilca I")
                .fecha(LocalDate.now().plusDays(1))
                .turno("MANANA")
                .estado("PENDIENTE_WEB")
                .build();
        when(registrarReservaPublicaUseCase.registrarReservaPublica(any())).thenReturn(ticket);

        String payload = """
                {
                    "nombres": "Paciente",
                    "apellidos": "Spam",
                    "dni": "88889999",
                    "telefono": "+51911222333",
                    "sedeId": "sede-chilca-1",
                    "fecha": "2026-10-25",
                    "turno": "MANANA",
                    "motivo": "Prueba spam"
                }
                """;

        // Realizar 5 peticiones permitidas desde la misma IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/reservas-publicas")
                    .with(req -> { req.setRemoteAddr("10.77.77.1"); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload));
        }

        // La 6ta petición debe responder 429 Too Many Requests
        mockMvc.perform(post("/reservas-publicas")
                        .with(req -> { req.setRemoteAddr("10.77.77.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("límite de intentos")));
    }

    @Test
    @DisplayName("(d.1) Consulta pública: Exige obligatoriamente ticket y DNI coincidente en el cuerpo (400 si falta alguno)")
    void testConsultaPublicaExigeTicketYDni() throws Exception {
        // Falta dni
        mockMvc.perform(post("/reservas-publicas/consulta")
                        .with(req -> { req.setRemoteAddr("10.66.66.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "ticket": "OM-12345678"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // Falta ticket
        mockMvc.perform(post("/reservas-publicas/consulta")
                        .with(req -> { req.setRemoteAddr("10.66.66.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "dni": "72345678"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("(d.2) Consulta pública: Con ticket y DNI válidos devuelve únicamente estado y sede")
    void testConsultaPublicaExitosa() throws Exception {
        ConsultaCitaResponseDTO consulta = ConsultaCitaResponseDTO.builder()
                .ticket("OM-ABCDEF12")
                .estado("CONFIRMADA")
                .fecha(LocalDate.now().plusDays(3))
                .turno("MANANA")
                .sedeId("sede-chilca-1")
                .sedeNombre("Chilca I (Principal)")
                .build();

        when(consultarReservaPublicaUseCase.consultarReservaPublica("OM-ABCDEF12", "72345678")).thenReturn(consulta);

        mockMvc.perform(post("/reservas-publicas/consulta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "ticket": "OM-ABCDEF12",
                                    "dni": "72345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("OM-ABCDEF12"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.sedeNombre").value("Chilca I (Principal)"))
                .andExpect(jsonPath("$.pacienteNombre").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }
}
