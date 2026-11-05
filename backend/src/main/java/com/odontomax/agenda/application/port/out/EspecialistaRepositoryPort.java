package com.odontomax.agenda.application.port.out;

import com.odontomax.agenda.domain.model.Especialista;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EspecialistaRepositoryPort {
    Optional<Especialista> findById(UUID id);
    Optional<Especialista> findByUsuarioId(UUID usuarioId);
    Optional<Especialista> findFirstByEmailIgnoreCaseAndUsuarioIdIsNull(String email);
    List<Especialista> findAllByOrderByNombreAsc();
    List<Especialista> findByActivoTrueOrderByNombreAsc();
    List<Especialista> findBySedeIdAndActivoTrue(String sedeId);
    boolean existsByCop(String cop);
    boolean existsByCopAndIdNot(String cop, UUID id);
    boolean existsById(UUID id);
    Especialista save(Especialista especialista);
    void delete(UUID id);
    List<String> findSedeIdsByEspecialistaId(UUID especialistaId);
    void vincularSede(UUID especialistaId, String sedeId);
    void desvincularSede(UUID especialistaId, String sedeId);
    void desvincularTodasLasSedes(UUID especialistaId);
    Map<String, Integer> findSillonesPorSede(UUID especialistaId);
    Optional<UUID> findEspecialistaIdBySedeYSillon(String sedeId, int sillonNumero);
    void actualizarSillon(UUID especialistaId, String sedeId, Integer sillonNumero);
}
