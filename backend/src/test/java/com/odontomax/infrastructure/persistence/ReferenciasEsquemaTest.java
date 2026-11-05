package com.odontomax.infrastructure.persistence;

import com.odontomax.agenda.infrastructure.persistence.repository.SpringDataEspecialistaRepository;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.Query;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guarda contra la deriva del esquema (sin Docker): las consultas de existencia que deciden entre
 * borrado físico y baja lógica / 409 deben cubrir TODAS las FK sin ON DELETE CASCADE que apuntan a la
 * tabla. Si una migración nueva añade otra referencia y no se agrega a la consulta, el DELETE físico
 * fallaría dentro de la transacción (rollback-only → 500), que es justo el bug que se corrigió.
 */
class ReferenciasEsquemaTest {

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)CREATE TABLE IF NOT EXISTS\\s+(\\w+)");
    private static final Pattern DROP_TABLE = Pattern.compile("(?i)DROP TABLE\\s+(?:IF EXISTS\\s+)?(\\w+)");
    private static final Pattern ALTER_TABLE = Pattern.compile("(?i)ALTER TABLE\\s+(?:IF EXISTS\\s+)?(\\w+)\\s+ADD COLUMN\\s+(?:IF NOT EXISTS\\s+)?(\\w+)");

    /** Pares "tabla.columna" que referencian a la tabla dada sin borrado en cascada. */
    private static Set<String> referenciasBloqueantes(String tablaReferida) throws Exception {
        Pattern referencia = Pattern.compile("(?i)REFERENCES\\s+" + tablaReferida + "\\s*\\(id\\)");
        Set<String> pares = new TreeSet<>();
        Set<String> tablasBorradas = new TreeSet<>();
        Resource[] migraciones = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql");
        assertTrue(migraciones.length > 0, "No se encontraron migraciones en el classpath");

        for (Resource migracion : migraciones) {
            String tablaActual = null;
            for (String linea : migracion.getContentAsString(StandardCharsets.UTF_8).split("\\R")) {
                Matcher drop = DROP_TABLE.matcher(linea);
                if (drop.find()) {
                    tablasBorradas.add(drop.group(1).toLowerCase());
                }
                Matcher create = CREATE_TABLE.matcher(linea);
                if (create.find()) {
                    tablaActual = create.group(1).toLowerCase();
                }
                if (!referencia.matcher(linea).find() || linea.toUpperCase().contains("ON DELETE CASCADE")) {
                    continue;
                }
                Matcher alter = ALTER_TABLE.matcher(linea);
                if (alter.find()) {
                    pares.add(alter.group(1).toLowerCase() + "." + alter.group(2).toLowerCase());
                } else if (tablaActual != null) {
                    pares.add(tablaActual + "." + linea.trim().split("\\s+")[0].toLowerCase());
                }
            }
        }
        // Una tabla que una migración posterior borró ya no puede bloquear ningún borrado.
        pares.removeIf(par -> tablasBorradas.contains(par.substring(0, par.indexOf('.'))));
        return pares;
    }

    private static String consulta(Class<?> repositorio, String metodo) throws Exception {
        return repositorio.getMethod(metodo, UUID.class).getAnnotation(Query.class).value();
    }

    private static void assertCubre(String sql, Set<String> pares) {
        String normalizado = sql.replaceAll("\\s+", " ");
        for (String par : pares) {
            String[] partes = par.split("\\.");
            String esperado = "FROM " + partes[0] + " WHERE " + partes[1] + " = :id";
            assertTrue(normalizado.contains(esperado),
                    "La consulta no cubre la referencia " + par + " (se esperaba '" + esperado + "')");
        }
    }

    @Test
    @DisplayName("Usuarios: la consulta de registros vinculados cubre todas las FK hacia usuarios sin cascada")
    void usuarios_consultaCubreTodasLasReferencias() throws Exception {
        Set<String> pares = referenciasBloqueantes("usuarios");

        // Verificación del propio parser: referencias conocidas deben detectarse y las de cascada excluirse
        assertTrue(pares.containsAll(Set.of("pagos.registrado_por", "notas_evolucion.autor_id",
                "tratamientos.descuento_autorizado_por", "especialistas.usuario_id")), pares::toString);
        assertFalse(pares.contains("password_historial.usuario_id"), pares::toString);

        assertCubre(consulta(SpringDataUsuarioRepository.class, "tieneRegistrosVinculados"), pares);
    }

    @Test
    @DisplayName("Especialistas: la consulta de historial clínico cubre todas las FK hacia especialistas sin cascada")
    void especialistas_consultaCubreTodasLasReferencias() throws Exception {
        Set<String> pares = referenciasBloqueantes("especialistas");

        assertTrue(pares.containsAll(Set.of("citas.doctor_id", "tratamientos.especialista_id")), pares::toString);
        assertFalse(pares.stream().anyMatch(p -> p.startsWith("especialista_sedes.") || p.startsWith("especialista_horarios.")),
                pares::toString);

        assertCubre(consulta(SpringDataEspecialistaRepository.class, "tieneHistorialClinico"), pares);
    }
}
