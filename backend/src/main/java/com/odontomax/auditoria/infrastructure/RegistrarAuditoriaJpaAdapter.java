package com.odontomax.auditoria.infrastructure;

import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Copia 1:1 los 10 campos a {@link AuditoriaLogEntity}. Sin defaults, sin try/catch y sin
 * {@code @Transactional}: el {@code save} se une a la transacción del llamador, como hasta ahora.
 */
@Component
@RequiredArgsConstructor
public class RegistrarAuditoriaJpaAdapter implements RegistrarAuditoriaApi {

    private final SpringDataAuditoriaRepository auditoriaRepository;

    @Override
    public void registrar(RegistroAuditoria registro) {
        auditoriaRepository.save(AuditoriaLogEntity.builder()
                .usuarioId(registro.usuarioId())
                .usuarioNombre(registro.usuarioNombre())
                .rol(registro.rol())
                .sedeId(registro.sedeId())
                .modulo(registro.modulo())
                .accion(registro.accion())
                .tabla(registro.tabla())
                .registroId(registro.registroId())
                .detalle(registro.detalle())
                .ip(registro.ip())
                .build());
    }
}
