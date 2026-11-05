package com.odontomax.agenda.caracterizacion;

import com.odontomax.agenda.application.dto.request.CitaRequestDTO;
import com.odontomax.agenda.application.dto.response.CitaResponseDTO;
import com.odontomax.agenda.application.port.in.AgendarCitaUseCase;
import com.odontomax.agenda.application.port.in.CambiarEstadoCitaUseCase;
import com.odontomax.agenda.application.port.in.IniciarAtencionCitaUseCase;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** V23 contra PostgreSQL real: cita enlazada a un plan, sesión sumada al atender y enlace que se suelta al borrar el plan. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CitaTratamientoPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", postgres::getUsername);
            registry.add("spring.flyway.password", postgres::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        }
    }

    private static final String SEDE = "sede-chilca-1";

    @Autowired private AgendarCitaUseCase agendar;
    @Autowired private CambiarEstadoCitaUseCase cambiarEstado;
    @Autowired private IniciarAtencionCitaUseCase iniciarAtencion;
    @Autowired private JdbcTemplate jdbc;

    private UUID pacienteId;
    private UUID otroPacienteId;
    private UUID planId;

    @BeforeEach
    void setUp() {
        ClinicaUserPrincipal admin = new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.test", "Admin", "ADMIN_GENERAL", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));

        pacienteId = insertarPaciente();
        otroPacienteId = insertarPaciente();
        planId = UUID.randomUUID();
        jdbc.update("INSERT INTO tratamientos (id, paciente_id, sede_id, nombre, costo_total, estado, total_sesiones, sesiones_realizadas) "
                + "VALUES (?, ?, ?, 'Ortodoncia', 1500, 'EN_PROCESO', 5, 2)", planId, pacienteId, SEDE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UUID insertarPaciente() {
        UUID id = UUID.randomUUID();
        String dni = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id) VALUES (?, ?, 'Prueba', 'Enlace', ?)", id, dni, SEDE);
        return id;
    }

    private CitaRequestDTO cita(UUID paciente, UUID tratamiento, LocalTime hora) {
        return CitaRequestDTO.builder()
                .pacienteId(paciente)
                .pacienteNombre("Prueba Enlace")
                .fecha(LocalDate.now().plusDays(7))
                .hora(hora)
                .motivoConsulta("Ortodoncia · sesión 3/5")
                .tratamientoId(tratamiento)
                .build();
    }

    private int sesiones() {
        return jdbc.queryForObject("SELECT sesiones_realizadas FROM tratamientos WHERE id = ?", Integer.class, planId);
    }

    @Test
    @DisplayName("La cita guarda tratamiento_id, atenderla suma la sesión una sola vez y borrar el plan suelta el enlace")
    void flujoCompleto() {
        CitaResponseDTO creada = agendar.agendarCita(cita(pacienteId, planId, LocalTime.of(9, 0)), SEDE);

        assertEquals(planId, creada.getTratamientoId());
        assertEquals(planId, jdbc.queryForObject("SELECT tratamiento_id FROM citas WHERE id = ?", UUID.class, creada.getId()));

        // B-028 / Paso 3: el flujo único pasa por cada estado: PROGRAMADA -> EN_SALA -> EN_ATENCION -> ATENDIDA
        cambiarEstado.cambiarEstado(creada.getId(), "EN_SALA", null);
        assertEquals(2, sesiones());

        iniciarAtencion.iniciarAtencion(creada.getId());
        cambiarEstado.cambiarEstado(creada.getId(), "ATENDIDA", null);
        assertEquals(3, sesiones());

        // Repetir el estado se rechaza (B-028) y no vuelve a sumar.
        assertThrows(IllegalArgumentException.class, () -> cambiarEstado.cambiarEstado(creada.getId(), "ATENDIDA", null));
        assertEquals(3, sesiones());

        // Borrar el plan conserva la cita y deja el enlace en NULL (ON DELETE SET NULL).
        jdbc.update("DELETE FROM tratamientos WHERE id = ?", planId);
        assertNull(jdbc.queryForObject("SELECT tratamiento_id FROM citas WHERE id = ?", UUID.class, creada.getId()));
    }

    @Test
    @DisplayName("No se puede enlazar la cita al plan de otro paciente")
    void planDeOtroPaciente() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agendar.agendarCita(cita(otroPacienteId, planId, LocalTime.of(10, 0)), SEDE));
        assertTrue(ex.getMessage().contains("no pertenece al paciente"));
    }

    @Test
    @DisplayName("Una cita sin plan funciona como antes y no toca ningún tratamiento")
    void citaSinPlan() {
        CitaResponseDTO creada = agendar.agendarCita(cita(pacienteId, null, LocalTime.of(11, 0)), SEDE);
        cambiarEstado.cambiarEstado(creada.getId(), "EN_SALA", null);
        iniciarAtencion.iniciarAtencion(creada.getId());
        cambiarEstado.cambiarEstado(creada.getId(), "ATENDIDA", null);

        assertNull(creada.getTratamientoId());
        assertEquals(2, sesiones());
    }

    @Test
    @DisplayName("V38 persiste llegada, inicio y fin en orden durante el recorrido de atención")
    void tiemposAtencionPersistenEnOrden() {
        CitaResponseDTO creada = agendar.agendarCita(cita(pacienteId, null, LocalTime.of(13, 0)), SEDE);

        CitaResponseDTO enSala = cambiarEstado.cambiarEstado(creada.getId(), "EN_SALA", null);
        assertNotNull(enSala.getLlegadaEn());
        assertNull(enSala.getInicioAtencionEn());
        assertNull(enSala.getFinAtencionEn());

        CitaResponseDTO enSillon = iniciarAtencion.iniciarAtencion(creada.getId());
        assertNotNull(enSillon.getInicioAtencionEn());
        assertTrue(enSillon.getLlegadaEn().isBefore(enSillon.getInicioAtencionEn()));

        CitaResponseDTO atendida = cambiarEstado.cambiarEstado(creada.getId(), "ATENDIDA", null);
        assertNotNull(atendida.getFinAtencionEn());
        assertTrue(atendida.getInicioAtencionEn().isBefore(atendida.getFinAtencionEn()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM citas WHERE id = ? AND llegada_en <= inicio_atencion_en "
                        + "AND inicio_atencion_en <= fin_atencion_en",
                Integer.class, creada.getId()));
    }
}
