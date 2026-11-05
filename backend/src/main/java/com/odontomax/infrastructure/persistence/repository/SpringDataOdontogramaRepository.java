package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.OdontogramaRegistroEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataOdontogramaRepository extends JpaRepository<OdontogramaRegistroEntity, UUID> {

    List<OdontogramaRegistroEntity> findByPacienteIdOrderByRegistradoEnDesc(UUID pacienteId);

    List<OdontogramaRegistroEntity> findByPacienteIdOrderByRegistradoEnAsc(UUID pacienteId);

    List<OdontogramaRegistroEntity> findByPacienteIdAndPiezaFdiOrderByRegistradoEnDesc(UUID pacienteId, Integer piezaFdi);

    /**
     * Hallazgos vigentes: el registro más reciente de cada pieza y tipo de hallazgo, siempre que esté activo y no lo haya
     * limpiado después un registro «sano» de esa pieza. Una pieza puede tener varios. Si una pieza se marcó «sano» y no
     * le quedó ningún hallazgo, se devuelve ese registro «sano» para que se vea como pieza sana y no como sin revisar.
     */
    @Query(value = """
        WITH actuales AS (
            SELECT o.*
            FROM (
                SELECT DISTINCT ON (pieza_fdi, estado) *
                FROM odontograma_registros
                WHERE paciente_id = :pacienteId AND estado <> 'sano'
                ORDER BY pieza_fdi, estado, registrado_en DESC, id
            ) o
            WHERE o.activo
              AND NOT EXISTS (
                  SELECT 1 FROM odontograma_registros s
                  WHERE s.paciente_id = :pacienteId AND s.pieza_fdi = o.pieza_fdi
                    AND s.estado = 'sano' AND s.registrado_en >= o.registrado_en)
        )
        SELECT * FROM actuales
        UNION ALL
        SELECT s.*
        FROM (
            SELECT DISTINCT ON (pieza_fdi) *
            FROM odontograma_registros
            WHERE paciente_id = :pacienteId AND estado = 'sano'
            ORDER BY pieza_fdi, registrado_en DESC, id
        ) s
        WHERE s.activo AND NOT EXISTS (SELECT 1 FROM actuales a WHERE a.pieza_fdi = s.pieza_fdi)
        ORDER BY pieza_fdi, estado
    """, nativeQuery = true)
    List<OdontogramaRegistroEntity> findEstadoActualPorPaciente(@Param("pacienteId") UUID pacienteId);
}
