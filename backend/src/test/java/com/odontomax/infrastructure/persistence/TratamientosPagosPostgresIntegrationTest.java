package com.odontomax.infrastructure.persistence;

import com.odontomax.application.dto.request.CajaAperturaRequestDTO;
import com.odontomax.application.dto.request.CajaCierreRequestDTO;
import com.odontomax.application.dto.request.PagoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoItemRequestDTO;
import com.odontomax.application.dto.response.CajaSesionResponseDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import com.odontomax.application.service.CajaService;
import com.odontomax.application.service.PagoService;
import com.odontomax.tratamientos.service.TratamientoService;
import com.odontomax.agenda.infrastructure.persistence.entity.EspecialistaEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class TratamientosPagosPostgresIntegrationTest {

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
    private TratamientoService tratamientoService;

    @Autowired
    private PagoService pagoService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private SpringDataPacienteRepository pacienteRepository;

    @Autowired
    private SpringDataUsuarioRepository usuarioRepository;

    @Autowired
    private SpringDataEspecialistaRepository especialistaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioEntity adminUser;
    private EspecialistaEntity especialista;
    private PacienteEntity paciente;

    @BeforeEach
    void setUp() {
        if (!postgres.isRunning()) {
            return;
        }

        adminUser = usuarioRepository.save(UsuarioEntity.builder()
                .email("admin.postgres@odontomax.pe")
                .nombre("Admin Postgres Test")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz123456")
                .rol("ADMIN_GENERAL")
                .sedeId("sede-chilca-1")
                .activo(true)
                .build());

        especialista = especialistaRepository.save(EspecialistaEntity.builder()
                .nombre("Dra. María Postgres")
                .especialidad("Ortodoncia")
                .activo(true)
                .build());

        paciente = pacienteRepository.save(PacienteEntity.builder()
                .dni("88990011")
                .nombres("Juan")
                .apellidos("Pérez Postgres")
                .sedeId("sede-chilca-1")
                .saldoPendiente(BigDecimal.ZERO)
                .build());

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                adminUser.getId(),
                adminUser.getEmail(),
                adminUser.getNombre(),
                "ADMIN_GENERAL",
                null
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Flujo completo: apertura en cero, tratamiento, cobro en efectivo y cierre")
    void flujoCompletoCajaTratamientoPago() {
        if (!postgres.isRunning()) {
            return;
        }
        Integer pagosHistoricosSinPlan = jdbc.queryForObject(
                "SELECT count(*) FROM pagos WHERE tratamiento_id IS NULL", Integer.class);
        assertEquals(0, pagosHistoricosSinPlan, "Pagos antiguos sin plan en la base limpia de prueba");

        // 1. Apertura de caja siempre en cero
        CajaSesionResponseDTO sesion = cajaService.abrirCaja(CajaAperturaRequestDTO.builder()
                .sedeId("sede-chilca-1")
                .montoApertura(new BigDecimal("100.00"))
                .observaciones("Apertura para prueba")
                .build());
        assertNotNull(sesion.getId());
        assertEquals("ABIERTA", sesion.getEstado());
        assertMonto(BigDecimal.ZERO, sesion.getMontoApertura());

        // 2. Crear tratamiento con item FDI
        TratamientoItemRequestDTO item = TratamientoItemRequestDTO.builder()
                .nombreProcedimiento("Curación con resina 3M")
                .piezaFdi(16)
                .precioBase(new BigDecimal("150.00"))
                .cantidad(1)
                .descuento(BigDecimal.ZERO)
                .observaciones("Pieza 16 oclusal")
                .build();

        TratamientoResponseDTO tratamiento = tratamientoService.crear(TratamientoCreateRequestDTO.builder()
                .pacienteId(paciente.getId())
                .sedeId("sede-chilca-1")
                .especialistaId(especialista.getId())
                .nombre("Tratamiento Restaurador")
                .costoTotal(new BigDecimal("150.00"))
                .items(List.of(item))
                .build());
        assertNotNull(tratamiento.getId());
        assertMonto(new BigDecimal("150.00"), tratamiento.getSaldoPendiente());

        // 3. Registrar pago en efectivo asociado al tratamiento
        PagoResponseDTO pago = pagoService.registrarPago(PagoCreateRequestDTO.builder()
                .pacienteId(paciente.getId())
                .sedeId("sede-chilca-1")
                .tratamientoId(tratamiento.getId())
                .monto(new BigDecimal("150.00"))
                .metodoPago("EFECTIVO")
                .comprobanteTipo("RECIBO")
                .cajaSesionId(sesion.getId())
                .build());
        assertNotNull(pago.getId());
        assertNotNull(pago.getComprobante());
        // El cobro sin doctor explícito se atribuye al doctor del tratamiento y queda guardado (V32)
        assertEquals(especialista.getId(), pago.getEspecialistaId());
        assertEquals(especialista.getNombre(), pago.getEspecialistaNombre());

        // 4. Verificar que el saldo del tratamiento quedó en 0
        TratamientoResponseDTO tratamientoActualizado = tratamientoService.obtenerPorId(tratamiento.getId());
        assertMonto(BigDecimal.ZERO, tratamientoActualizado.getSaldoPendiente());
        assertMonto(new BigDecimal("150.00"), tratamientoActualizado.getMontoPagado());

        // 5. El esperado es solo el cobro: no existe fondo de apertura.
        CajaSesionResponseDTO cierre = cajaService.cerrarCaja(sesion.getId(), CajaCierreRequestDTO.builder()
                .montoContado(new BigDecimal("150.00"))
                .observaciones("Cuadre perfecto")
                .build());
        assertEquals("CERRADA", cierre.getEstado());
        assertMonto(new BigDecimal("150.00"), cierre.getMontoEsperado());
        assertMonto(new BigDecimal("150.00"), cierre.getMontoContado());
        assertMonto(BigDecimal.ZERO, cierre.getDiferencia());
    }

    // BigDecimal.equals distingue escala (0 vs 0.00); los montos se comparan por valor
    private static void assertMonto(BigDecimal esperado, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, esperado.compareTo(actual), "esperado " + esperado + " pero fue " + actual);
    }
}
