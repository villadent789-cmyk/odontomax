package com.odontomax.agenda.caracterizacion;

import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.agenda.infrastructure.adapter.UsuarioOdontologoListener;
import com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaHorarioEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaHorarioRepository;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.identidad.testsupport.UsuarioTestFixtures;
import org.springframework.context.ApplicationContext;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class UsuarioOdontologoSyncPostgresIntegrationTest {

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

    @Autowired
    private GestionarUsuarioUseCase usuarioService;

    @SpyBean
    private UsuarioOdontologoListener usuarioOdontologoListener;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SpringDataEspecialistaRepository especialistaRepository;

    @Autowired
    private SpringDataEspecialistaHorarioRepository especialistaHorarioRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ClinicaUserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        reset(usuarioOdontologoListener);

        adminPrincipal = new ClinicaUserPrincipal(
                UUID.randomUUID(),
                "admin.caract@odontomax.pe",
                "Admin Caracterizacion",
                "ADMIN_GENERAL",
                null
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                adminPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))
        ));
    }

    @AfterEach
    void tearDown() {
        reset(usuarioOdontologoListener);
        SecurityContextHolder.clearContext();
    }

    @Autowired
    private com.odontomax.agenda.application.service.EspecialistaApplicationService especialistaService;

    @Test
    @DisplayName("Todo servicio del tarifario (y los que se creen) existe en el catálogo al que apuntan los planes de tratamiento")
    void serviciosDelTarifarioSirvenParaPlanes() {
        Integer sinCatalogo = jdbc.queryForObject(
                "SELECT count(*) FROM servicios_web s WHERE s.activo AND NOT EXISTS (SELECT 1 FROM catalogo_procedimientos c WHERE c.id = s.id)",
                Integer.class);
        assertEquals(0, sinCatalogo, "Hay servicios activos que no se pueden usar en un plan (FK)");

        jdbc.update("INSERT INTO servicios_web (id, nombre, especialidad, duracion_min, precio_desde) VALUES ('tar-prueba-plan', 'Servicio nuevo de prueba', 'Especialidad nueva', 30, 50)");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM catalogo_procedimientos WHERE id = 'tar-prueba-plan'", Integer.class));
        // Dos servicios con el mismo nombre (uno antiguo desactivado y otro nuevo) no deben romper la sincronización.
        jdbc.update("INSERT INTO servicios_web (id, nombre, especialidad, duracion_min, activo) VALUES ('srv-repetido', 'Nombre repetido', 'Endodoncia', 30, false)");
        jdbc.update("INSERT INTO servicios_web (id, nombre, especialidad, duracion_min) VALUES ('tar-repetido', 'Nombre repetido', 'Endodoncia', 30)");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM catalogo_procedimientos WHERE id IN ('srv-repetido','tar-repetido')", Integer.class));
        jdbc.update("DELETE FROM catalogo_procedimientos WHERE id IN ('srv-repetido','tar-repetido')");
        jdbc.update("DELETE FROM servicios_web WHERE id IN ('srv-repetido','tar-repetido')");
        jdbc.update("DELETE FROM catalogo_procedimientos WHERE id = 'tar-prueba-plan'");
        jdbc.update("DELETE FROM servicios_web WHERE id = 'tar-prueba-plan'");
    }

    @Test
    @DisplayName("Recorrido completo: odontólogo nuevo con especialidad queda con sede, sillón libre y visible en la web; el doctor edita su especialidad; al desactivarlo sale de la web")
    void recorridoCompletoDelDoctor() {
        jdbc.update("UPDATE especialista_sedes SET sillon_numero = NULL WHERE sede_id = 'sede-chilca-1'");
        String email = "dr.recorrido@" + UUID.randomUUID() + ".pe";
        UsuarioResponseDTO usuario = usuarioService.crearUsuario(UsuarioCreateRequestDTO.builder()
                .nombre("Dra. Recorrido Completo").email(email).password("Password123!")
                .rol("ODONTOLOGO").sedeId("sede-chilca-1").especialidad("Ortodoncia").build(), adminPrincipal, "127.0.0.1");

        EspecialistaEntity esp = especialistaRepository.findByUsuarioId(usuario.getId()).orElseThrow();
        assertEquals("Ortodoncia", esp.getEspecialidad());
        Integer sillon = jdbc.queryForObject(
                "SELECT sillon_numero FROM especialista_sedes WHERE especialista_id = ? AND sede_id = 'sede-chilca-1'",
                Integer.class, esp.getId());
        assertEquals(1, sillon, "Se le asigna el primer sillón libre de la sede");
        assertTrue(especialistaRepository.findBySedeIdAndActivoTrue("sede-chilca-1").stream().anyMatch(e -> e.getId().equals(esp.getId())),
                "Sale en la lista pública de su sede");
        assertFalse(especialistaRepository.findBySedeIdAndActivoTrue("sede-eltambo").stream().anyMatch(e -> e.getId().equals(esp.getId())),
                "No sale en otra sede");

        especialistaService.actualizarMiEspecialidad(usuario.getId(), "Odontopediatría");
        assertEquals("Odontopediatría", especialistaRepository.findByUsuarioId(usuario.getId()).orElseThrow().getEspecialidad());

        usuarioService.cambiarEstado(usuario.getId(), false, adminPrincipal, "127.0.0.1");
        assertFalse(especialistaRepository.findBySedeIdAndActivoTrue("sede-chilca-1").stream().anyMatch(e -> e.getId().equals(esp.getId())),
                "Al desactivar la cuenta deja de salir en la web");
    }

    @Test
    @DisplayName("a) Crear usuario ODONTOLOGO crea automáticamente especialista con horarios Lun-Dom 09:00-20:00 (V24)")
    void crearUsuarioOdontologo_creaEspecialistaYHorarios() {
        String email = "dr.andres.sync@" + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO dto = UsuarioCreateRequestDTO.builder()
                .nombre("Dr. Andrés Sync")
                .email(email)
                .password("Password123!")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .build();

        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(dto, adminPrincipal, "127.0.0.1");
        assertNotNull(usuarioCreado.getId());

        // 1. Verificar perfil de especialista en base de datos PostgreSQL
        Optional<EspecialistaEntity> espOpt = especialistaRepository.findByUsuarioId(usuarioCreado.getId());
        assertTrue(espOpt.isPresent(), "Debe existir un especialista asociado al usuario odontólogo");

        EspecialistaEntity esp = espOpt.get();
        assertEquals("Dr. Andrés Sync", esp.getNombre());
        assertEquals(email.toLowerCase(), esp.getEmail().toLowerCase());
        assertTrue(esp.isActivo());

        // 2. Verificar sede asociada
        List<String> sedes = especialistaRepository.findSedeIdsByEspecialistaId(esp.getId());
        assertTrue(sedes.contains("sede-chilca-1"));

        // 3. Verificar 7 bloques de horario por defecto (Lunes=1 a Domingo=7, 09:00-20:00, V24)
        List<EspecialistaHorarioEntity> horarios = especialistaHorarioRepository.findByEspecialistaIdAndActivoTrue(esp.getId());
        assertEquals(7, horarios.size(), "Debe tener 7 bloques horarios por defecto (Lun a Dom)");

        for (int dia = 1; dia <= 7; dia++) {
            final int d = dia;
            EspecialistaHorarioEntity h = horarios.stream()
                    .filter(item -> item.getDiaSemana() == d)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Falta bloque para día " + d));
            assertEquals(LocalTime.of(9, 0), h.getHoraInicio());
            assertEquals(LocalTime.of(20, 0), h.getHoraFin());
            assertEquals("sede-chilca-1", h.getSedeId());
            assertTrue(h.isActivo());
        }
    }

    @Test
    @DisplayName("b) Fallo forzado al crear especialista produce ROLLBACK completo del usuario")
    void forzarFalloCreacionEspecialista_rollbackCompletoUsuario() {
        String email = "dr.fallo." + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO dto = UsuarioCreateRequestDTO.builder()
                .nombre("Dr. Fallo Seguro")
                .email(email)
                .password("Password123!")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .build();

        doThrow(new IllegalStateException("Error forzado en creación de especialista"))
                .when(usuarioOdontologoListener).onUsuarioOdontologoRegistrado(any());

        assertThrows(IllegalStateException.class, () ->
                usuarioService.crearUsuario(dto, adminPrincipal, "127.0.0.1")
        );

        // Verificar que el usuario NO quedó persistido en la base de datos (rollback transaccional)
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, email.toLowerCase()
        );
        assertEquals(0, count, "El usuario debe haber sufrido rollback y no existir en la base de datos");
    }

    @Test
    @DisplayName("c) Desactivar usuario ODONTOLOGO desactiva automáticamente su perfil de especialista")
    void desactivarUsuario_desactivaEspecialista() {
        String email = "dr.desactivar." + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO createDto = UsuarioCreateRequestDTO.builder()
                .nombre("Dr. Temporal Activo")
                .email(email)
                .password("Password123!")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .build();

        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(createDto, adminPrincipal, "127.0.0.1");
        UUID usuarioId = usuarioCreado.getId();

        EspecialistaEntity espAntes = especialistaRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertTrue(espAntes.isActivo());

        // Desactivar el usuario
        UsuarioUpdateRequestDTO updateDto = UsuarioUpdateRequestDTO.builder()
                .nombre("Dr. Temporal Inactivo")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .activo(false)
                .build();

        usuarioService.actualizarUsuario(usuarioId, updateDto, adminPrincipal, "127.0.0.1");

        // Verificar que el usuario quedó inactivo
        var userInBd = UsuarioTestFixtures.buscarPorId(context, usuarioId).orElseThrow();
        assertFalse(userInBd.isActivo(), "El usuario debe estar inactivo");

        // Verificar que el especialista asociado quedó inactivo
        EspecialistaEntity espDespues = especialistaRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertFalse(espDespues.isActivo(), "El especialista debe haberse sincronizado y quedar inactivo");
    }

    @Test
    @DisplayName("d) Reactivar usuario ODONTOLOGO reactiva automáticamente su perfil de especialista")
    void reactivarUsuario_reactivaEspecialista() {
        String email = "dr.reactivar." + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO createDto = UsuarioCreateRequestDTO.builder()
                .nombre("Dr. Temporal Inactivo-Activo")
                .email(email)
                .password("Password123!")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .build();

        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(createDto, adminPrincipal, "127.0.0.1");
        UUID usuarioId = usuarioCreado.getId();

        // 1. Desactivar
        usuarioService.cambiarEstado(usuarioId, false, adminPrincipal, "127.0.0.1");
        EspecialistaEntity espInactivo = especialistaRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertFalse(espInactivo.isActivo(), "El especialista debe estar inactivo tras desactivar usuario");

        // 2. Reactivar
        usuarioService.cambiarEstado(usuarioId, true, adminPrincipal, "127.0.0.1");
        EspecialistaEntity espReactivado = especialistaRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertTrue(espReactivado.isActivo(), "El especialista debe reactivarse automáticamente al reactivar usuario");
    }

    @Test
    @DisplayName("e) Crear usuario con rol NO ODONTOLOGO (RECEPCION) no crea perfil de especialista")
    void crearUsuarioRecepcion_noCreaEspecialista() {
        String email = "recepcion.sync." + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO createDto = UsuarioCreateRequestDTO.builder()
                .nombre("Recepcionista Prueba")
                .email(email)
                .password("Password123!")
                .rol("RECEPCION")
                .sedeId("sede-chilca-1")
                .build();

        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(createDto, adminPrincipal, "127.0.0.1");
        assertNotNull(usuarioCreado.getId());

        Optional<EspecialistaEntity> espOpt = especialistaRepository.findByUsuarioId(usuarioCreado.getId());
        assertTrue(espOpt.isEmpty(), "No debe crearse ningún perfil de especialista para rol RECEPCION");
    }

    @Test
    @DisplayName("f) Actualizar datos de usuario ODONTOLOGO sincroniza nombre y nueva sede en especialista")
    void actualizarUsuario_sincronizaNombreYSedeEspecialista() {
        String email = "dr.update.sync." + UUID.randomUUID() + ".pe";
        UsuarioCreateRequestDTO createDto = UsuarioCreateRequestDTO.builder()
                .nombre("Dr. Nombre Original")
                .email(email)
                .password("Password123!")
                .rol("ODONTOLOGO")
                .sedeId("sede-chilca-1")
                .build();

        UsuarioResponseDTO usuarioCreado = usuarioService.crearUsuario(createDto, adminPrincipal, "127.0.0.1");
        UUID usuarioId = usuarioCreado.getId();

        // Actualizar nombre a Dr. Nombre Actualizado y sede a sede-eltambo
        UsuarioUpdateRequestDTO updateDto = UsuarioUpdateRequestDTO.builder()
                .nombre("Dr. Nombre Actualizado")
                .rol("ODONTOLOGO")
                .sedeId("sede-eltambo")
                .activo(true)
                .build();

        usuarioService.actualizarUsuario(usuarioId, updateDto, adminPrincipal, "127.0.0.1");

        EspecialistaEntity espActualizado = especialistaRepository.findByUsuarioId(usuarioId).orElseThrow();
        assertEquals("Dr. Nombre Actualizado", espActualizado.getNombre(), "El nombre del especialista debe haberse sincronizado");

        List<String> sedes = especialistaRepository.findSedeIdsByEspecialistaId(espActualizado.getId());
        assertTrue(sedes.contains("sede-eltambo"), "La nueva sede debe haberse vinculado al especialista");
    }
}
