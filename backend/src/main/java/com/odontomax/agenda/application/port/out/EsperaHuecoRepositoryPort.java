package com.odontomax.agenda.application.port.out;

import com.odontomax.agenda.domain.model.EsperaHueco;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EsperaHuecoRepositoryPort {

    EsperaHueco save(EsperaHueco espera);

    Optional<EsperaHueco> findById(UUID id);

    /** Las que siguen vigentes (activas u ofrecidas) de la sede, de la más antigua a la más nueva. */
    List<EsperaHueco> findVigentesBySede(String sedeId);

    boolean existeVigenteDelPaciente(String sedeId, UUID pacienteId);
}
