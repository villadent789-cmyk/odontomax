package com.odontomax.laboratorio.caracterizacion;

import com.odontomax.application.dto.request.CajaAperturaRequestDTO;
import com.odontomax.application.dto.response.CajaSesionResponseDTO;
import com.odontomax.application.service.CajaService;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.infrastructure.persistence.entity.OrdenLaboratorioEntity;
import com.odontomax.infrastructure.persistence.entity.PagoLaboratorioEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataOrdenLaboratorioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoLaboratorioRepository;
import com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException;
import com.odontomax.laboratorio.dto.AnularPagoLaboratorioRequestDTO;
import com.odontomax.laboratorio.dto.PagoLaboratorioResponseDTO;
import com.odontomax.laboratorio.service.LaboratorioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificación de integración con PostgreSQL real (Testcontainers):
 * Reversa en caja y anulación de pagos de laboratorio mediante LaboratorioService.anularPagoLaboratorio.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AnularPagoLaboratorioCajaPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb_lab_caja")
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

    private static final String SEDE_CHILCA = "sede-chilca-1";
    private static final String SEDE_ELTAMBO = "sede-eltambo";
    private static final UUID LAB_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ESPECIALISTA_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private LaboratorioService laboratorioService;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private SpringDataPagoLaboratorioRepository pagoLaboratorioRepository;

    @Autowired
    private SpringDataOrdenLaboratorioRepository ordenRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private ClinicaUserPrincipal crearYAutenticarUsuario(String rol, String sedeId) {
        UUID id = UUID.randomUUID();
        String email = rol.toLowerCase().replace('_', '-') + "-" + id.toString().substring(0, 8) + "@odontomax.pe";
        String nombre = "Usuario " + rol + " " + id.toString().substring(0, 4);
        jdbc.update("INSERT INTO usuarios (id, nombre, email, rol, sede_id, activo) VALUES (?, ?, ?, ?, ?, true)",
                id, nombre, email, rol, sedeId);

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                id, email, nombre, rol, sedeId
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        return principal;
    }

    private UUID crearPaciente(String sedeId) {
        UUID id = UUID.randomUUID();
        String dni = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        jdbc.update("INSERT INTO pacientes (id, dni, nombres, apellidos, sede_id) VALUES (?, ?, 'Paciente', 'Lab', ?)",
                id, dni, sedeId);
        return id;
    }

    private void cerrarCajas(String sedeId) {
        jdbc.update("UPDATE caja_sesiones SET cerrada_en = now() WHERE sede_id = ? AND cerrada_en IS NULL", sedeId);
    }

    private UUID abrirCaja(String sedeId, ClinicaUserPrincipal user) {
        cerrarCajas(sedeId);
        CajaSesionResponseDTO sesion = cajaService.abrirCaja(CajaAperturaRequestDTO.builder()
                .sedeId(sedeId)
                .observaciones("Apertura para prueba de laboratorio")
                .build());
        return sesion.getId();
    }

    private OrdenLaboratorioEntity crearOrden(String sedeId, UUID pacienteId) {
        return ordenRepository.save(OrdenLaboratorioEntity.builder()
                .codigo("LAB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .pacienteId(pacienteId)
                .especialistaId(ESPECIALISTA_ID)
                .sedeId(sedeId)
                .laboratorioId(LAB_ID)
                .tipoTrabajo("Corona Zirconio")
                .costoLaboratorio(new BigDecimal("250.00"))
                .precioPaciente(new BigDecimal("600.00"))
                .estado("ENVIADO")
                .build());
    }

    private PagoLaboratorioEntity crearPago(UUID ordenId, String sedeId, BigDecimal monto, String metodo, UUID registradoPor) {
        return pagoLaboratorioRepository.save(PagoLaboratorioEntity.builder()
                .ordenId(ordenId)
                .laboratorioId(LAB_ID)
                .sedeId(sedeId)
                .monto(monto)
                .metodoPago(metodo)
                .registradoPor(registradoPor)
                .anulado(false)
                .build());
    }

    @Test
    @DisplayName("1. Pago en EFECTIVO con caja abierta: crea UN movimiento INGRESO en sesión abierta y pago queda anulado")
    void anularPagoEfectivoConCajaAbierta() {
        ClinicaUserPrincipal admin = crearYAutenticarUsuario("ADMIN_GENERAL", null);
        UUID sesionId = abrirCaja(SEDE_CHILCA, admin);
        UUID pacienteId = crearPaciente(SEDE_CHILCA);
        OrdenLaboratorioEntity orden = crearOrden(SEDE_CHILCA, pacienteId);
        BigDecimal monto = new BigDecimal("120.00");
        PagoLaboratorioEntity pago = crearPago(orden.getId(), SEDE_CHILCA, monto, "EFECTIVO", admin.id());

        String motivo = "Error de digitación en comprobante";
        AnularPagoLaboratorioRequestDTO req = new AnularPagoLaboratorioRequestDTO(motivo);
        PagoLaboratorioResponseDTO res = laboratorioService.anularPagoLaboratorio(pago.getId(), req, admin);

        assertThat(res.getAnulado()).isTrue();
        assertThat(res.getMotivoAnulacion()).isEqualTo(motivo);
        assertThat(res.getAnuladoPor()).isEqualTo(admin.id());
        assertThat(res.getAnuladoEn()).isNotNull();

        PagoLaboratorioEntity pagoBd = pagoLaboratorioRepository.findById(pago.getId()).orElseThrow();
        assertThat(pagoBd.getAnulado()).isTrue();
        assertThat(pagoBd.getMotivoAnulacion()).isEqualTo(motivo);
        assertThat(pagoBd.getAnuladoPor()).isEqualTo(admin.id());
        assertThat(pagoBd.getAnuladoEn()).isNotNull();

        List<Map<String, Object>> movimientos = jdbc.queryForList(
                "SELECT * FROM caja_movimientos WHERE referencia_id = ?", pago.getId());
        assertThat(movimientos).hasSize(1);
        Map<String, Object> mov = movimientos.get(0);
        assertThat(mov.get("tipo")).isEqualTo("INGRESO");
        assertThat((BigDecimal) mov.get("monto")).isEqualByComparingTo(monto);
        assertThat(mov.get("metodo_pago")).isEqualTo("EFECTIVO");
        assertThat(mov.get("referencia_tipo")).isEqualTo("PAGO_LABORATORIO");
        assertThat(mov.get("referencia_id")).isEqualTo(pago.getId());
        assertThat((UUID) mov.get("sesion_id")).isEqualTo(sesionId);
    }

    @Test
    @DisplayName("2. Pago en EFECTIVO con caja cerrada: responde 409, pago NO queda anulado y no crea movimientos")
    void anularPagoEfectivoConCajaCerradaLanza409() {
        ClinicaUserPrincipal admin = crearYAutenticarUsuario("ADMIN_GENERAL", null);
        cerrarCajas(SEDE_CHILCA);
        UUID pacienteId = crearPaciente(SEDE_CHILCA);
        OrdenLaboratorioEntity orden = crearOrden(SEDE_CHILCA, pacienteId);
        BigDecimal monto = new BigDecimal("150.00");
        PagoLaboratorioEntity pago = crearPago(orden.getId(), SEDE_CHILCA, monto, "EFECTIVO", admin.id());

        AnularPagoLaboratorioRequestDTO req = new AnularPagoLaboratorioRequestDTO("Reversa fallida");

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pago.getId(), req, admin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("No se puede anular un pago en EFECTIVO porque no existe ninguna caja abierta");
                });

        PagoLaboratorioEntity pagoBd = pagoLaboratorioRepository.findById(pago.getId()).orElseThrow();
        assertThat(pagoBd.getAnulado()).isFalse();
        assertThat(pagoBd.getMotivoAnulacion()).isNull();
        assertThat(pagoBd.getAnuladoPor()).isNull();

        Integer movCount = jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class, pago.getId());
        assertThat(movCount).isEqualTo(0);
    }

    @Test
    @DisplayName("3. Pago en YAPE con caja cerrada: reversa en sesión automática y pago queda anulado")
    void anularPagoDigitalConCajaCerradaUsaSesionAutomatica() {
        ClinicaUserPrincipal admin = crearYAutenticarUsuario("ADMIN_GENERAL", null);
        cerrarCajas(SEDE_CHILCA);
        UUID pacienteId = crearPaciente(SEDE_CHILCA);
        OrdenLaboratorioEntity orden = crearOrden(SEDE_CHILCA, pacienteId);
        BigDecimal monto = new BigDecimal("200.00");
        PagoLaboratorioEntity pago = crearPago(orden.getId(), SEDE_CHILCA, monto, "YAPE", admin.id());

        AnularPagoLaboratorioRequestDTO req = new AnularPagoLaboratorioRequestDTO("Devolución por cambio de orden");
        PagoLaboratorioResponseDTO res = laboratorioService.anularPagoLaboratorio(pago.getId(), req, admin);

        assertThat(res.getAnulado()).isTrue();

        PagoLaboratorioEntity pagoBd = pagoLaboratorioRepository.findById(pago.getId()).orElseThrow();
        assertThat(pagoBd.getAnulado()).isTrue();
        assertThat(pagoBd.getMotivoAnulacion()).isEqualTo("Devolución por cambio de orden");

        List<Map<String, Object>> movimientos = jdbc.queryForList(
                "SELECT * FROM caja_movimientos WHERE referencia_id = ?", pago.getId());
        assertThat(movimientos).hasSize(1);
        Map<String, Object> mov = movimientos.get(0);
        assertThat(mov.get("tipo")).isEqualTo("INGRESO");
        assertThat((BigDecimal) mov.get("monto")).isEqualByComparingTo(monto);
        assertThat(mov.get("metodo_pago")).isEqualTo("YAPE");
        assertThat(mov.get("referencia_tipo")).isEqualTo("PAGO_LABORATORIO");
        assertThat(mov.get("sesion_id")).isNotNull();
    }

    @Test
    @DisplayName("4. Anular dos veces el mismo pago: la segunda falla y NO crea un segundo movimiento")
    void anularDosVecesFallaYSoloMantieneUnMovimiento() {
        ClinicaUserPrincipal admin = crearYAutenticarUsuario("ADMIN_GENERAL", null);
        abrirCaja(SEDE_CHILCA, admin);
        UUID pacienteId = crearPaciente(SEDE_CHILCA);
        OrdenLaboratorioEntity orden = crearOrden(SEDE_CHILCA, pacienteId);
        BigDecimal monto = new BigDecimal("90.00");
        PagoLaboratorioEntity pago = crearPago(orden.getId(), SEDE_CHILCA, monto, "EFECTIVO", admin.id());

        laboratorioService.anularPagoLaboratorio(pago.getId(), new AnularPagoLaboratorioRequestDTO("Primera anulación"), admin);
        Integer countDespuesDePrimera = jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class, pago.getId());
        assertThat(countDespuesDePrimera).isEqualTo(1);

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pago.getId(), new AnularPagoLaboratorioRequestDTO("Segunda anulación"), admin))
                .isInstanceOf(com.odontomax.laboratorio.domain.exception.PagoLaboratorioNoPermitidoException.class)
                .hasMessageContaining("ya ha sido anulado previamente");

        Integer countDespuesDeSegunda = jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class, pago.getId());
        assertThat(countDespuesDeSegunda).isEqualTo(1);
    }

    @Test
    @DisplayName("5. ADMIN_SEDE de otra sede no puede anular el pago (validarAccesoSede)")
    void adminSedeDeOtraSedeNoPuedeAnular() {
        ClinicaUserPrincipal adminGeneral = crearYAutenticarUsuario("ADMIN_GENERAL", null);
        abrirCaja(SEDE_CHILCA, adminGeneral);
        UUID pacienteId = crearPaciente(SEDE_CHILCA);
        OrdenLaboratorioEntity orden = crearOrden(SEDE_CHILCA, pacienteId);
        BigDecimal monto = new BigDecimal("130.00");
        PagoLaboratorioEntity pago = crearPago(orden.getId(), SEDE_CHILCA, monto, "EFECTIVO", adminGeneral.id());

        ClinicaUserPrincipal adminOtraSede = crearYAutenticarUsuario("ADMIN_SEDE", SEDE_ELTAMBO);

        assertThatThrownBy(() -> laboratorioService.anularPagoLaboratorio(pago.getId(), new AnularPagoLaboratorioRequestDTO("Intento otra sede"), adminOtraSede))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no tiene permisos para acceder a la sede");

        PagoLaboratorioEntity pagoBd = pagoLaboratorioRepository.findById(pago.getId()).orElseThrow();
        assertThat(pagoBd.getAnulado()).isFalse();

        Integer movCount = jdbc.queryForObject(
                "SELECT count(*) FROM caja_movimientos WHERE referencia_id = ?", Integer.class, pago.getId());
        assertThat(movCount).isEqualTo(0);
    }
}
