package com.odontomax.tratamientos.service;

import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.tratamientos.dto.PacienteSinPlanDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pacientes con hallazgos por tratar en el odontograma (caries, fracturas, restauraciones en mal estado…) a quienes nadie
 * les armó todavía un plan ni un presupuesto: los que el doctor no debería dejar pasar.
 */
@Service
@RequiredArgsConstructor
public class PacientesSinPlanService {

    /** Hallazgos «en mal estado o por hacer» del odontograma: los que la norma pinta de rojo (la movilidad es azul y no cuenta). */
    static final List<String> POR_TRATAR = List.of(
            "caries", "fosas_fisuras", "fractura", "restauracion_mal_estado", "corona_mal_estado", "endodoncia_mal_estado",
            "remanente_radicular", "implante_mal_estado", "sellante_mal_estado", "protesis_mal_estado",
            "aparato_mal_estado", "pulpotomia_mal_estado", "restauracion_temporal", "corona_temporal");

    private static final int MAXIMO = 30;

    private final JdbcTemplate jdbc;
    private final ConsultaEspecialistasModuloApi especialistasApi;

    @Transactional(readOnly = true)
    public List<PacienteSinPlanDTO> listar(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        String marcas = POR_TRATAR.stream().map(x -> "?").collect(Collectors.joining(","));
        // Estado vigente del odontograma: el último registro de cada pieza y hallazgo, activo y no limpiado después por «sano».
        String sql = """
            WITH vigentes AS (
                SELECT o.paciente_id, o.estado
                FROM (
                    SELECT DISTINCT ON (paciente_id, pieza_fdi, estado) *
                    FROM odontograma_registros
                    WHERE estado <> 'sano'
                    ORDER BY paciente_id, pieza_fdi, estado, registrado_en DESC, id
                ) o
                WHERE o.activo
                  AND NOT EXISTS (
                      SELECT 1 FROM odontograma_registros s
                      WHERE s.paciente_id = o.paciente_id AND s.pieza_fdi = o.pieza_fdi
                        AND s.estado = 'sano' AND s.registrado_en >= o.registrado_en)
            )
            SELECT p.id, p.nombres, p.apellidos, p.telefono, count(*) AS hallazgos, array_agg(DISTINCT v.estado) AS tipos
            FROM vigentes v
            JOIN pacientes p ON p.id = v.paciente_id
            WHERE p.sede_id = ? AND p.archivado = false AND v.estado IN (%s)
              AND NOT EXISTS (SELECT 1 FROM tratamientos t WHERE t.paciente_id = p.id AND t.estado IN ('PLANIFICADO', 'EN_PROCESO'))
              AND NOT EXISTS (SELECT 1 FROM presupuestos pr WHERE pr.paciente_id = p.id AND pr.estado = 'VIGENTE')
            GROUP BY p.id, p.nombres, p.apellidos, p.telefono
            ORDER BY count(*) DESC, p.apellidos, p.nombres
            LIMIT 200
            """.formatted(marcas);

        List<Object> parametros = new ArrayList<>();
        parametros.add(sedeId);
        parametros.addAll(POR_TRATAR);

        List<PacienteSinPlanDTO> todos = jdbc.query(sql, (rs, i) -> PacienteSinPlanDTO.builder()
                .pacienteId(rs.getObject("id", UUID.class))
                .pacienteNombre((rs.getString("nombres") + " " + rs.getString("apellidos")).trim())
                .telefono(rs.getString("telefono"))
                .hallazgos(rs.getInt("hallazgos"))
                .tipos(Arrays.asList((String[]) rs.getArray("tipos").getArray()))
                .build(), parametros.toArray());

        // Un doctor solo ve a sus pacientes (los que tienen cita con él).
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        if (usuario != null && "ODONTOLOGO".equalsIgnoreCase(usuario.rol())) {
            Set<UUID> suyos = especialistasApi.pacientesDelOdontologo(usuario.id());
            todos = todos.stream().filter(p -> suyos.contains(p.getPacienteId())).toList();
        }
        return todos.stream().limit(MAXIMO).toList();
    }
}
