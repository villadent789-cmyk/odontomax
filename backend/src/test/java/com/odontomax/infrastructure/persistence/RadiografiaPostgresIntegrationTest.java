package com.odontomax.infrastructure.persistence;

import com.odontomax.application.service.RadiografiaService;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.domain.model.Radiografia;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class RadiografiaPostgresIntegrationTest {

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
            registry.add("spring.flyway.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        }
    }

    @Autowired
    private RadiografiaService radiografiaService;

    @Autowired
    private SpringDataPacienteRepository pacienteRepository;

    @Autowired
    private SpringDataEspecialistaRepository especialistaRepository;

    @Autowired
    private RadiografiaRepositoryPort radiografiaRepository;

    @MockBean
    private StoragePort storagePort;

    private UUID usuarioId;
    private UUID especialistaId;
    private UUID pacienteId;

    @BeforeEach
    void setUp() {
        usuarioId = UUID.randomUUID();
        especialistaId = UUID.randomUUID();
        pacienteId = UUID.randomUUID();

        // Configurar almacenamiento mock
        when(storagePort.uploadFile(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storagePort.generatePresignedUrl(anyString(), anyInt()))
                .thenReturn("https://r2.privado/radiografia.png?token=valido");

        // Autenticar usuario con usuarioId
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                usuarioId, "doctor@odontomax.pe", "Dr. Especialista", "ODONTOLOGO", "sede-chilca-1"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO")))
        );
    }

    @Test
    @DisplayName("Subida con PostgreSQL real valida FK de especialista y CHECK constraints de tipo_estudio")
    void testSubirRadiografiaPostgresReal() throws Exception {
        // 1. Crear paciente real
        PacienteEntity paciente = PacienteEntity.builder()
                .id(pacienteId)
                .dni("76543210")
                .nombres("Paciente")
                .apellidos("Prueba")
                .sedeId("sede-chilca-1")
                .build();
        // El id es @GeneratedValue: Hibernate asigna uno nuevo, se usa el devuelto por save()
        pacienteId = pacienteRepository.save(paciente).getId();

        // 2. Crear especialista real vinculado al usuario_id
        EspecialistaEntity esp = EspecialistaEntity.builder()
                .id(especialistaId)
                .usuarioId(usuarioId)
                .nombre("Dr. Especialista")
                .especialidad("Ortodoncia")
                .activo(true)
                .build();
        especialistaId = especialistaRepository.save(esp).getId();

        jdbcTemplate.update("INSERT INTO citas (id, paciente_id, paciente_nombre, doctor_id, sede_id, motivo_consulta, fecha, hora, duracion_minutos, estado, adelanto_requerido, adelanto_monto, adelanto_estado, estado_confirmacion) VALUES (?, ?, 'Paciente Prueba', ?, 'sede-chilca-1', 'Control', CURRENT_DATE, '10:00', 30, 'PROGRAMADA', false, 0, 'NO_APLICA', 'SIN_CONTACTAR')", UUID.randomUUID(), pacienteId, especialistaId);

        // 3. Subir archivo radiográfico con firma válida PNG
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("archivo", "estudio.png", "image/png", validPng);

        Radiografia guardada = radiografiaService.subirRadiografia(
                pacienteId, null, "PANORAMICA", 11, "INICIAL", "Diagnóstico clínico", file
        );

        assertNotNull(guardada);
        assertNotNull(guardada.getId());
        assertEquals("PANORAMICA", guardada.getTipoEstudio());
        assertEquals(especialistaId, guardada.getDoctorId(), "doctor_id debe referenciar a especialistas(id), no usuarios(id)");

        // 4. Verificar que se puede consultar de PostgreSQL
        Radiografia encontrada = radiografiaRepository.findById(guardada.getId()).orElse(null);
        assertNotNull(encontrada);
        assertEquals(especialistaId, encontrada.getDoctorId());

        // 5. Eliminación física: desaparece de la BD, queda auditada y se borra el objeto en R2 tras el commit
        radiografiaService.eliminarRadiografia(guardada.getId(), "Estudio cargado por error");

        assertTrue(radiografiaRepository.findById(guardada.getId()).isEmpty());
        Integer auditorias = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria_log WHERE accion = 'ELIMINAR_RADIOGRAFIA' AND registro_id = ? AND detalle LIKE '%cargado por error%'",
                Integer.class, guardada.getId().toString());
        assertEquals(1, auditorias);
        org.mockito.Mockito.verify(storagePort).deleteFile(org.mockito.ArgumentMatchers.startsWith("pacientes/" + pacienteId + "/"));
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
}
