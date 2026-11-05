package com.odontomax.agenda.application.service;

import com.odontomax.agenda.api.CobroDeCitaModuloApi;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.ComisionCitaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Genera la comisión de Villamax cuando el cobro que faltaba se registra después de atender la cita. Corre al terminar
 * la transacción del cobro (así ve el saldo ya actualizado) y en una transacción propia: si falla, el cobro queda intacto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CobroDeCitaApplicationService implements CobroDeCitaModuloApi {

    private final CitaRepositoryPort citaRepository;
    private final ComisionCitaPort comisionCitaPort;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void alRegistrarseCobro(UUID tratamientoId) {
        if (tratamientoId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generar(tratamientoId);
                }
            });
        } else {
            generar(tratamientoId);
        }
    }

    private void generar(UUID tratamientoId) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(estado -> citaRepository.findAtendidasByTratamientoId(tratamientoId)
                    .forEach(cita -> comisionCitaPort.generarComisionSiAplica(cita.getId())));
        } catch (RuntimeException e) {
            log.error("No se pudo generar la comisión tras el cobro del plan {}", tratamientoId, e);
        }
    }
}
