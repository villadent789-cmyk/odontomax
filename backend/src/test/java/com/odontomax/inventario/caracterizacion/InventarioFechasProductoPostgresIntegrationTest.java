package com.odontomax.inventario.caracterizacion;

import com.odontomax.application.dto.response.ProductoInventarioResponseDTO;
import com.odontomax.infrastructure.persistence.entity.InventarioMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.ProductoInventarioEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataInventarioMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataProductoInventarioRepository;
import com.odontomax.inventario.service.InventarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Próximo vencimiento y última entrega por producto, calculados por la consulta agregada contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InventarioFechasProductoPostgresIntegrationTest {

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

    private static final String CHILCA = "sede-chilca-1";
    private static final String HUANCAYO = "sede-eltambo";

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private SpringDataProductoInventarioRepository productoRepository;

    @Autowired
    private SpringDataInventarioMovimientoRepository movimientoRepository;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));

    private void movimiento(UUID productoId, String sedeId, String tipo, int cantidad, LocalDate vence, LocalDate entrega) {
        movimientoRepository.save(InventarioMovimientoEntity.builder()
                .productoId(productoId).sedeId(sedeId).tipo(tipo).cantidad(cantidad)
                .fechaVencimiento(vence).fechaEntrega(entrega).build());
    }

    private ProductoInventarioResponseDTO producto(String sedeId, UUID id) {
        return inventarioService.listarProductos(sedeId, null).stream()
                .filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("El próximo vencimiento ignora fechas pasadas y salidas; la última entrega es la más reciente; ambos respetan la sede")
    void fechasPorProductoYSede() {
        ProductoInventarioEntity conFechas = productoRepository.save(ProductoInventarioEntity.builder()
                .codigo("FEC-" + UUID.randomUUID().toString().substring(0, 8)).nombre("Resina con fechas").build());
        ProductoInventarioEntity sinFechas = productoRepository.save(ProductoInventarioEntity.builder()
                .codigo("SIN-" + UUID.randomUUID().toString().substring(0, 8)).nombre("Guantes sin fechas").build());
        UUID id = conFechas.getId();

        movimiento(id, CHILCA, "ENTRADA", 10, hoy.minusDays(10), hoy.minusDays(30));
        movimiento(id, CHILCA, "ENTRADA", 10, hoy.plusDays(40), hoy.minusDays(5));
        movimiento(id, CHILCA, "ENTRADA", 10, hoy.plusDays(10), hoy.minusDays(20));
        movimiento(id, CHILCA, "SALIDA", -1, hoy.plusDays(1), hoy);
        movimiento(id, HUANCAYO, "ENTRADA", 5, hoy.plusDays(3), hoy.minusDays(1));
        movimiento(sinFechas.getId(), CHILCA, "ENTRADA", 8, null, null);

        ProductoInventarioResponseDTO todas = producto(null, id);
        assertThat(todas.getProximoVencimiento()).isEqualTo(hoy.plusDays(3));
        assertThat(todas.getUltimaEntrega()).isEqualTo(hoy.minusDays(1));

        ProductoInventarioResponseDTO chilca = producto(CHILCA, id);
        assertThat(chilca.getProximoVencimiento()).isEqualTo(hoy.plusDays(10));
        assertThat(chilca.getUltimaEntrega()).isEqualTo(hoy.minusDays(5));

        ProductoInventarioResponseDTO huancayo = producto(HUANCAYO, id);
        assertThat(huancayo.getProximoVencimiento()).isEqualTo(hoy.plusDays(3));
        assertThat(huancayo.getUltimaEntrega()).isEqualTo(hoy.minusDays(1));

        ProductoInventarioResponseDTO sin = producto(null, sinFechas.getId());
        assertThat(sin.getProximoVencimiento()).isNull();
        assertThat(sin.getUltimaEntrega()).isNull();
    }
}
