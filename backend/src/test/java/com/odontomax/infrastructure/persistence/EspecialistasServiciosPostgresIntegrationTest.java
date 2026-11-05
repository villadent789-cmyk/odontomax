package com.odontomax.infrastructure.persistence;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.SillonesEspecialistaRequestDTO;
import com.odontomax.sedes.dto.ServicioRequestDTO;
import com.odontomax.agenda.application.service.EspecialistaApplicationService;
import com.odontomax.sedes.service.ServicioService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gestión de especialistas, horarios y servicios contra PostgreSQL real con el esquema
 * creado por las migraciones Flyway, no por Hibernate.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class EspecialistasServiciosPostgresIntegrationTest {

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

    @Autowired private EspecialistaApplicationService especialistaService;
    @Autowired private ServicioService servicioService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void autenticarAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ClinicaUserPrincipal(UUID.randomUUID(), "admin@odontomax.pe", "Admin General", "ADMIN_GENERAL", null),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private HorarioEspecialistaRequestDTO bloque(String sede, int dia, String inicio, String fin) {
        return HorarioEspecialistaRequestDTO.builder().sedeId(sede).diaSemana(dia)
                .horaInicio(LocalTime.parse(inicio)).horaFin(LocalTime.parse(fin)).build();
    }

    private int horariosDe(UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM especialista_horarios WHERE especialista_id = ?", Integer.class, id);
    }

    @Test
    @DisplayName("Especialista: crear, horarios, quitar sede (cascada), desactivar y auditoría")
    void cicloCompletoEspecialista() {
        var creado = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Dra. Elena Gómez").especialidad("Endodoncia").cop("COP-77777")
                .telefono("987654321").sedes(List.of("sede-chilca-1", "sede-eltambo")).build());
        UUID id = creado.getId();
        assertEquals(2, creado.getSedes().size());

        // COP duplicado
        assertThrows(IllegalArgumentException.class, () -> especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Otro").especialidad("General").cop("COP-77777").sedes(List.of("sede-chilca-1")).build()));

        // Horario inicial y reemplazo sobre los mismos días (restricción única especialista+sede+día)
        especialistaService.reemplazarHorarios(id, List.of(
                bloque("sede-chilca-1", 1, "09:00", "13:00"),
                bloque("sede-eltambo", 2, "10:00", "14:00")));
        var reemplazado = especialistaService.reemplazarHorarios(id, List.of(
                bloque("sede-chilca-1", 1, "14:00", "18:00"),
                bloque("sede-eltambo", 2, "08:00", "12:00")));
        assertEquals(2, reemplazado.size());
        assertEquals(2, horariosDe(id));
        assertEquals(LocalTime.of(14, 0), especialistaService.obtenerHorarios(id).get(0).getHoraInicio());

        // Disponibilidad real el próximo lunes en Chilca 1: turnos de 14:00 a 18:00
        LocalDate proximoLunes = LocalDate.now().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        var disp = especialistaService.obtenerDisponibilidad(id, proximoLunes, "sede-chilca-1");
        assertTrue(disp.isAtiendeEseDia());
        assertEquals(8, disp.getSlots().size());

        // Quitar la sede El Tambo borra en cascada su horario en esa sede
        especialistaService.actualizar(id, EspecialistaRequestDTO.builder()
                .nombre("Dra. Elena Gómez Paz").especialidad("Endodoncia").cop("COP-77777")
                .sedes(List.of("sede-chilca-1")).build());
        assertEquals(1, horariosDe(id));
        assertEquals(List.of("sede-chilca-1"), especialistaService.listarTodos().stream()
                .filter(e -> e.getId().equals(id)).findFirst().orElseThrow().getSedes());

        // Desactivar: desaparece del catálogo público y no ofrece turnos, pero sigue en administración
        especialistaService.cambiarEstado(id, false);
        assertTrue(especialistaService.listarPublicos().stream().noneMatch(e -> e.getId().equals(id)));
        assertTrue(especialistaService.listarTodos().stream().anyMatch(e -> e.getId().equals(id) && !e.isActivo()));
        assertFalse(especialistaService.obtenerDisponibilidad(id, proximoLunes, "sede-chilca-1").isAtiendeEseDia());

        // Registros descriptivos de la aplicación (el trigger auditar_cambio de la tabla añade además
        // sus propios registros INSERT/UPDATE con datos antes/después)
        Integer auditorias = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auditoria_log WHERE registro_id = ? AND accion IN "
                        + "('CREAR_ESPECIALISTA','ACTUALIZAR_HORARIO_ESPECIALISTA','EDITAR_ESPECIALISTA','DESACTIVAR_ESPECIALISTA')",
                Integer.class, id.toString());
        assertEquals(5, auditorias, "crear + 2 horarios + editar + desactivar");
        Integer auditoriaTrigger = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auditoria_log WHERE registro_id = ? AND tabla = 'especialistas' AND datos_despues IS NOT NULL",
                Integer.class, id.toString());
        assertTrue(auditoriaTrigger >= 1, "el trigger de BD también audita los cambios de la tabla");
    }

    private String crearSedePrueba(int sillones) {
        String id = "sede-test-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sedes (id, nombre, direccion, ciudad, telefono, unidad_de_negocio, sillones, activa) "
                + "VALUES (?, 'Sede Test', 'Dir Test', 'Huancayo', '999888777', 'VILLADENT', ?, true)", id, sillones);
        return id;
    }

    @Test
    @DisplayName("V34: asigna sillón por sede y la migración mantiene los sillones iniciales de Doctor A y B")
    void migracionV34YAsignacionPorSede() {
        UUID doctorA = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID doctorB = UUID.fromString("10000000-0000-0000-0000-000000000002");
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM especialista_sedes WHERE especialista_id = ?", Integer.class, doctorA) > 0);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM especialista_sedes WHERE especialista_id = ? "
                        + "AND (sillon_numero IS NULL OR sillon_numero <> 1)",
                Integer.class, doctorA));
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM especialista_sedes WHERE especialista_id = ?", Integer.class, doctorB) > 0);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM especialista_sedes WHERE especialista_id = ? "
                        + "AND (sillon_numero IS NULL OR sillon_numero <> 2)",
                Integer.class, doctorB));

        String sedePrueba = crearSedePrueba(3);
        var creado = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Doctor sillón fijo " + UUID.randomUUID())
                .especialidad("Odontología General")
                .sedes(List.of(sedePrueba))
                .build());
        var actualizado = especialistaService.actualizarSillones(creado.getId(),
                new SillonesEspecialistaRequestDTO(Map.of(sedePrueba, 3), List.of()));

        assertEquals(3, actualizado.getSillonesPorSede().get(sedePrueba));
        assertEquals(3, jdbc.queryForObject(
                "SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = ?",
                Integer.class, creado.getId(), sedePrueba));
    }

    @Test
    @DisplayName("V34: el índice único impide que dos especialistas compartan sillón en una sede")
    void indiceUnicoImpideCompartirSillon() {
        String sedeId = crearSedePrueba(4);
        int sillonLibre = 1;
        var primero = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Doctor índice uno " + UUID.randomUUID())
                .especialidad("Odontología General").sedes(List.of(sedeId)).build());
        var segundo = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Doctor índice dos " + UUID.randomUUID())
                .especialidad("Odontología General").sedes(List.of(sedeId)).build());

        jdbc.update("UPDATE especialista_sedes SET sillon_numero = ? WHERE especialista_id = ? AND sede_id = ?",
                sillonLibre, primero.getId(), sedeId);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE especialista_sedes SET sillon_numero = ? WHERE especialista_id = ? AND sede_id = ?",
                        sillonLibre, segundo.getId(), sedeId));
    }

        @Test
    @DisplayName("Paso C: doctores activos por sede para la reserva pública (Chilca 4, Huancayo 2, Villamax 1)")
    void doctoresPorSedeEnReservaPublica() {
        jdbc.update("DELETE FROM especialistas WHERE id NOT IN ("
                + "'10000000-0000-0000-0000-000000000001', "
                + "'10000000-0000-0000-0000-000000000002', "
                + "'10000000-0000-0000-0000-000000000003', "
                + "'10000000-0000-0000-0000-000000000004', "
                + "'10000000-0000-0000-0000-000000000005', "
                + "'10000000-0000-0000-0000-000000000006', "
                + "'10000000-0000-0000-0000-000000000007')");
        var chilca = especialistaService.listarPublicosPorSede("sede-chilca-1");
        var huancayo = especialistaService.listarPublicosPorSede("sede-eltambo");
        var villamax = especialistaService.listarPublicosPorSede("sede-villamax-chilca");

        assertEquals(4, chilca.size());
        org.assertj.core.api.Assertions.assertThat(chilca.stream().map(com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO::getNombre).toList())
                .containsExactly("Doctor A", "Doctor B", "Doctor C", "Doctor D");

        assertEquals(2, huancayo.size());
        org.assertj.core.api.Assertions.assertThat(huancayo.stream().map(com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO::getNombre).toList())
                .containsExactly("Doctor E", "Doctor F");

        assertEquals(1, villamax.size());
        org.assertj.core.api.Assertions.assertThat(villamax.stream().map(com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO::getNombre).toList())
                .containsExactly("Doctor Villamax");
    }

    @Test
    @DisplayName("V42: existen 6 doctores oficiales (4 en Chilca, 2 en Huancayo) con sillones distintos por sede")
    void seisDoctoresOficialesConSillonesPorSede() {
        UUID docA = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID docB = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID docC = UUID.fromString("10000000-0000-0000-0000-000000000003");
        UUID docD = UUID.fromString("10000000-0000-0000-0000-000000000004");
        UUID docE = UUID.fromString("10000000-0000-0000-0000-000000000005");
        UUID docF = UUID.fromString("10000000-0000-0000-0000-000000000006");

        assertEquals(1, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-chilca-1'", Integer.class, docA));
        assertEquals(2, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-chilca-1'", Integer.class, docB));
        assertEquals(3, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-chilca-1'", Integer.class, docC));
        assertEquals(4, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-chilca-1'", Integer.class, docD));

        assertEquals(1, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-eltambo'", Integer.class, docE));
        assertEquals(2, jdbc.queryForObject("SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-eltambo'", Integer.class, docF));

        assertEquals(4, jdbc.queryForObject("SELECT COUNT(DISTINCT sillon_numero) FROM especialista_sedes WHERE sede_id = 'sede-chilca-1'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT sillon_numero) FROM especialista_sedes WHERE sede_id = 'sede-eltambo'", Integer.class));
    }

    @Test
    @DisplayName("V39: guarda y borra la meta y la comisión del doctor, y la base rechaza valores fuera de rango")
    void metaYComisionDelDoctor() {
        UUID id = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Doctor meta " + UUID.randomUUID())
                .especialidad("Odontología General").sedes(List.of("sede-chilca-1")).build()).getId();
        assertNull(especialistaService.obtenerMeta(id).getMetaMensual());

        var guardada = especialistaService.actualizarMeta(id, com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO.builder()
                .metaMensual(new java.math.BigDecimal("20000")).comisionExcedentePorcentaje(new java.math.BigDecimal("10")).build());
        assertEquals(0, new java.math.BigDecimal("20000").compareTo(guardada.getMetaMensual()));
        assertEquals(0, new java.math.BigDecimal("10").compareTo(
                especialistaService.obtenerMeta(id).getComisionExcedentePorcentaje()));

        assertThrows(IllegalArgumentException.class, () -> especialistaService.actualizarMeta(id,
                com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO.builder()
                        .comisionExcedentePorcentaje(new java.math.BigDecimal("101")).build()));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE especialistas SET meta_mensual = -1 WHERE id = ?", id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE especialistas SET comision_excedente_porcentaje = 150 WHERE id = ?", id));

        especialistaService.actualizarMeta(id, new com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO());
        assertNull(especialistaService.obtenerMeta(id).getMetaMensual());
        assertNull(especialistaService.obtenerMeta(id).getComisionExcedentePorcentaje());
    }

    @Test
    @DisplayName("Horario en una sede no asignada es rechazado y no borra el horario existente")
    void horarioSedeNoAsignada_noBorraNada() {
        UUID id = especialistaService.crear(EspecialistaRequestDTO.builder()
                .nombre("Dr. Mario Soto").especialidad("General").sedes(List.of("sede-villamax-chilca")).build()).getId();
        especialistaService.reemplazarHorarios(id, List.of(bloque("sede-villamax-chilca", 3, "09:00", "12:00")));

        assertThrows(IllegalArgumentException.class, () ->
                especialistaService.reemplazarHorarios(id, List.of(bloque("sede-eltambo", 3, "09:00", "12:00"))));
        assertEquals(1, horariosDe(id));
    }

    @Test
    @DisplayName("Servicios: crear con id legible único, editar y desactivar")
    void cicloServicios() {
        var s1 = servicioService.crear(ServicioRequestDTO.builder()
                .nombre("Limpieza Dental Básica").especialidad("General").duracionMin(30).precio(new BigDecimal("80.00")).build());
        var s2 = servicioService.crear(ServicioRequestDTO.builder()
                .nombre("Limpieza dental básica").especialidad("General").duracionMin(45).build());
        assertEquals("limpieza-dental-basica", s1.getId());
        assertEquals("limpieza-dental-basica-2", s2.getId());

        var editado = servicioService.actualizar(s1.getId(), ServicioRequestDTO.builder()
                .nombre("Limpieza Dental Profunda").especialidad("General").duracionMin(60).precio(new BigDecimal("120.00")).build());
        assertEquals(60, editado.getDuracionMin());
        assertEquals(0, new BigDecimal("120.00").compareTo(editado.getPrecio()));

        servicioService.cambiarEstado(s1.getId(), false);
        assertTrue(servicioService.listarServicios().stream().noneMatch(s -> s.getId().equals(s1.getId())));
        assertTrue(servicioService.listarTodos().stream().anyMatch(s -> s.getId().equals(s1.getId()) && !s.isActivo()));
    }

    @Test
    @DisplayName("V51 no pisa horarios ni datos que el administrador ya cambió")
    void v51NoPisaCambiosDelAdministrador() throws Exception {
        jdbc.update("UPDATE especialista_horarios SET hora_inicio = TIME '10:00', hora_fin = TIME '14:00' "
                + "WHERE especialista_id = '10000000-0000-0000-0000-000000000005' AND sede_id = 'sede-eltambo' AND dia_semana = 1");
        jdbc.update("UPDATE especialistas SET nombre = 'Dra. Renombrada' WHERE id = '10000000-0000-0000-0000-000000000007'");
        try {

        String sql = new String(new org.springframework.core.io.ClassPathResource("db/migration/V51__doctor_villamax_y_horarios.sql")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        jdbc.execute(sql);

        var horario = jdbc.queryForMap("SELECT hora_inicio, hora_fin FROM especialista_horarios "
                + "WHERE especialista_id = '10000000-0000-0000-0000-000000000005' AND sede_id = 'sede-eltambo' AND dia_semana = 1");
        org.assertj.core.api.Assertions.assertThat(horario.get("hora_inicio").toString()).startsWith("10:00");
        org.assertj.core.api.Assertions.assertThat(horario.get("hora_fin").toString()).startsWith("14:00");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT nombre FROM especialistas WHERE id = '10000000-0000-0000-0000-000000000007'", String.class))
                .isEqualTo("Dra. Renombrada");
        } finally {
            jdbc.update("UPDATE especialistas SET nombre = 'Doctor Villamax' WHERE id = '10000000-0000-0000-0000-000000000007'");
        }
    }
}
