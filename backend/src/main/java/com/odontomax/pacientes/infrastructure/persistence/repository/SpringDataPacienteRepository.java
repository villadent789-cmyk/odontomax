package com.odontomax.pacientes.infrastructure.persistence.repository;

import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataPacienteRepository extends JpaRepository<PacienteEntity, UUID> {

    /**
     * Busca por DNI para la reserva pública, que solo atiende a Villadent. El mismo documento puede existir además en
     * Villamax (otro negocio, otra ficha), así que la búsqueda se acota a la clínica.
     */
    @Query("SELECT p FROM PacienteEntity p WHERE p.dni = :dni AND p.unidadDeNegocio = 'VILLADENT'")
    Optional<PacienteEntity> findByDni(@Param("dni") String dni);

    List<PacienteEntity> findBySedeIdAndArchivadoFalse(String sedeId);

    List<PacienteEntity> findByArchivadoFalse();

    default List<PacienteEntity> findAllActivos(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return findByArchivadoFalse();
        }
        return findBySedeIdAndArchivadoFalse(sedeId);
    }

    default List<PacienteEntity> findActivosBySede(String sedeId) {
        return findBySedeIdAndArchivadoFalse(sedeId);
    }

    default List<PacienteEntity> searchByTerminoFiltro(String termino, String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return searchByTerminoAllSedes(termino);
        }
        return searchByTermino(termino, sedeId);
    }

    @Query("SELECT p FROM PacienteEntity p WHERE p.sedeId = :sedeId AND p.archivado = false " +
           "AND (CAST(FUNCTION('translate', LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR CAST(FUNCTION('translate', LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR p.dni LIKE CONCAT('%', :termino, '%'))")
    List<PacienteEntity> searchByTermino(@Param("termino") String termino, @Param("sedeId") String sedeId);

    @Query("SELECT p FROM PacienteEntity p WHERE p.archivado = false " +
           "AND (CAST(FUNCTION('translate', LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR CAST(FUNCTION('translate', LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR p.dni LIKE CONCAT('%', :termino, '%'))")
    List<PacienteEntity> searchByTerminoAllSedes(@Param("termino") String termino);

    @Query("SELECT p FROM PacienteEntity p WHERE (:sedeId IS NULL OR p.sedeId = :sedeId) AND p.archivado = false " +
           "AND (:termino IS NULL OR :termino = '' OR CAST(FUNCTION('translate', LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR CAST(FUNCTION('translate', LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR p.dni LIKE CONCAT('%', :termino, '%'))")
    org.springframework.data.domain.Page<PacienteEntity> searchPaginado(
            @Param("termino") String termino,
            @Param("sedeId") String sedeId,
            org.springframework.data.domain.Pageable pageable);

        @Query(value = "SELECT p.* FROM pacientes p " +
           "WHERE (:sedeId IS NULL OR p.sede_id = :sedeId) AND p.archivado = false " +
           "AND (:termino IS NULL OR :termino = '' " +
           "     OR translate(LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR translate(LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR p.dni LIKE CONCAT('%', :termino, '%')) " +
           "AND EXISTS (SELECT 1 FROM citas c JOIN especialistas e ON e.id = c.doctor_id WHERE c.paciente_id = p.id AND e.usuario_id = :usuarioId)",
           countQuery = "SELECT COUNT(p.id) FROM pacientes p " +
           "WHERE (:sedeId IS NULL OR p.sede_id = :sedeId) AND p.archivado = false " +
           "AND (:termino IS NULL OR :termino = '' " +
           "     OR translate(LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR translate(LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR p.dni LIKE CONCAT('%', :termino, '%')) " +
           "AND EXISTS (SELECT 1 FROM citas c JOIN especialistas e ON e.id = c.doctor_id WHERE c.paciente_id = p.id AND e.usuario_id = :usuarioId)",
           nativeQuery = true)
    org.springframework.data.domain.Page<PacienteEntity> searchPaginadoOdontologo(
            @Param("termino") String termino,
            @Param("sedeId") String sedeId,
            @Param("usuarioId") UUID usuarioId,
            org.springframework.data.domain.Pageable pageable);

    @Query(value = "SELECT p.* FROM pacientes p " +
           "WHERE (:sedeId IS NULL OR p.sede_id = :sedeId) AND p.archivado = false " +
           "AND (:termino IS NULL OR :termino = '' " +
           "     OR translate(LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR translate(LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') LIKE translate(LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') " +
           "     OR p.dni LIKE CONCAT('%', :termino, '%')) " +
           "AND EXISTS (SELECT 1 FROM citas c JOIN especialistas e ON e.id = c.doctor_id WHERE c.paciente_id = p.id AND e.usuario_id = :usuarioId) " +
           "ORDER BY p.nombres ASC, p.apellidos ASC",
           nativeQuery = true)
    List<PacienteEntity> searchOdontologo(
            @Param("termino") String termino,
            @Param("sedeId") String sedeId,
            @Param("usuarioId") UUID usuarioId);

    @Query("SELECT p FROM PacienteEntity p WHERE (:sedeId IS NULL OR p.sedeId = :sedeId) AND p.archivado = false " +
           "AND p.fechaNacimiento IS NOT NULL " +
           "AND EXTRACT(MONTH FROM p.fechaNacimiento) = :mes " +
           "AND EXTRACT(DAY FROM p.fechaNacimiento) = :dia " +
           "AND p.consentimientoPromociones = true")
    List<PacienteEntity> findCumpleanerosDelDia(
            @Param("sedeId") String sedeId,
            @Param("mes") int mes,
            @Param("dia") int dia);
    // ---- Vistas "todas las sedes" acotadas a un negocio (Villadent o Villamax) ----

    @Query("SELECT p FROM PacienteEntity p WHERE p.archivado = false AND p.unidadDeNegocio = :negocio")
    List<PacienteEntity> findActivosDelNegocio(@Param("negocio") String negocio);

    @Query("SELECT p FROM PacienteEntity p WHERE p.archivado = false AND p.unidadDeNegocio = :negocio " +
           "AND (CAST(FUNCTION('translate', LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR CAST(FUNCTION('translate', LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR p.dni LIKE CONCAT('%', :termino, '%'))")
    List<PacienteEntity> searchByTerminoDelNegocio(@Param("termino") String termino, @Param("negocio") String negocio);

    @Query("SELECT p FROM PacienteEntity p WHERE p.archivado = false AND p.unidadDeNegocio = :negocio " +
           "AND (:termino IS NULL OR :termino = '' OR CAST(FUNCTION('translate', LOWER(p.nombres), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR CAST(FUNCTION('translate', LOWER(p.apellidos), 'áéíóúüñ', 'aeiouun') AS string) LIKE CAST(FUNCTION('translate', LOWER(CONCAT('%', COALESCE(:termino, ''), '%')), 'áéíóúüñ', 'aeiouun') AS string) " +
           "OR p.dni LIKE CONCAT('%', :termino, '%'))")
    org.springframework.data.domain.Page<PacienteEntity> searchPaginadoDelNegocio(
            @Param("termino") String termino,
            @Param("negocio") String negocio,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM PacienteEntity p WHERE p.archivado = false AND p.unidadDeNegocio = :negocio " +
           "AND p.fechaNacimiento IS NOT NULL " +
           "AND EXTRACT(MONTH FROM p.fechaNacimiento) = :mes " +
           "AND EXTRACT(DAY FROM p.fechaNacimiento) = :dia " +
           "AND p.consentimientoPromociones = true")
    List<PacienteEntity> findCumpleanerosDelDiaDelNegocio(
            @Param("negocio") String negocio,
            @Param("mes") int mes,
            @Param("dia") int dia);
}
