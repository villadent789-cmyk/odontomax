package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.pacientes.domain.model.RegistrosProtegidos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ExpedienteDependenciasAdapterTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final RadiografiaRepositoryPort radiografiaRepository = mock(RadiografiaRepositoryPort.class);
    private final ExpedienteDependenciasAdapter adapter = new ExpedienteDependenciasAdapter(entityManager, radiografiaRepository);
    private final UUID pacienteId = UUID.randomUUID();

    /** Sentencias DELETE ejecutadas contra el EntityManager simulado. */
    private final List<String> deletesEjecutados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        deletesEjecutados.clear();
    }

    /** Los COUNT devuelven el valor indicado por tabla (0 si no se indica) y los DELETE se registran. */
    private void simularBaseDeDatos(Map<String, Long> conteos) {
        when(entityManager.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Query query = mock(Query.class);
            lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
            if (sql.startsWith("SELECT COUNT(*) FROM ")) {
                String tabla = sql.substring("SELECT COUNT(*) FROM ".length()).split(" ")[0];
                lenient().when(query.getSingleResult()).thenReturn(conteos.getOrDefault(tabla, 0L));
            } else if (sql.startsWith("DELETE")) {
                lenient().when(query.executeUpdate()).thenAnswer(x -> {
                    deletesEjecutados.add(sql);
                    return 1;
                });
            }
            return query;
        });
    }

    @Test
    @DisplayName("Cuenta cada tabla protegida por separado")
    void contarRegistrosProtegidos() {
        simularBaseDeDatos(Map.of("notas_evolucion", 1L, "antecedentes_historial", 2L, "historia_clinica_accesos", 3L,
                "pagos", 4L, "consentimientos", 5L, "ordenes_laboratorio", 6L));

        assertEquals(new RegistrosProtegidos(1, 2, 3, 4, 5, 6), adapter.contarRegistrosProtegidos(pacienteId));
    }

    @Test
    @DisplayName("Elimina radiografías, promociones, citas, odontograma y tratamientos; nunca consentimientos")
    void eliminarDependientes() {
        simularBaseDeDatos(Map.of());

        adapter.eliminarDependientes(pacienteId);

        assertEquals(5, deletesEjecutados.size());
        assertTrue(deletesEjecutados.stream().anyMatch(s -> s.contains("FROM citas")));
        assertTrue(deletesEjecutados.stream().anyMatch(s -> s.contains("FROM tratamientos")));
        assertTrue(deletesEjecutados.stream().anyMatch(s -> s.contains("FROM promociones_paciente")));
        assertTrue(deletesEjecutados.stream().anyMatch(s -> s.contains("FROM radiografias_estudios")));
        assertTrue(deletesEjecutados.stream().anyMatch(s -> s.contains("FROM odontograma_registros")));
        assertTrue(deletesEjecutados.stream().noneMatch(s -> s.contains("FROM consentimientos")));
    }

    @Test
    @DisplayName("Archivos radiográficos: se omiten las URLs vacías")
    void archivosRadiograficos() {
        when(radiografiaRepository.findByPacienteId(pacienteId)).thenReturn(List.of(
                Radiografia.builder().archivoUrl("r2/rx/rx1.png").build(),
                Radiografia.builder().archivoUrl("").build(),
                Radiografia.builder().build()));

        assertEquals(List.of("r2/rx/rx1.png"), adapter.archivosRadiograficos(pacienteId));
    }

    @Test
    @DisplayName("Archivos radiográficos: un error de consulta se propaga")
    void archivosRadiograficosErrorSePropaga() {
        when(radiografiaRepository.findByPacienteId(pacienteId)).thenThrow(new RuntimeException("timeout"));

        assertThrows(RuntimeException.class, () -> adapter.archivosRadiograficos(pacienteId));
    }
}
