package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.domain.port.out.StoragePort;
import com.odontomax.pacientes.application.port.out.BorradoArchivosPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArchivosR2Adapter implements BorradoArchivosPort {

    private final StoragePort storagePort;

    @Override
    public void borrarTrasCommit(List<String> urls) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar(urls);
                }
            });
        } else {
            borrar(urls);
        }
    }

    private void borrar(List<String> archivos) {
        for (String url : archivos) {
            try {
                storagePort.deleteFile(url);
                log.info("Archivo de radiografía eliminado de Cloudflare R2 tras commit exitoso: {}", url);
            } catch (Exception ex) {
                log.warn("No se pudo eliminar el archivo R2 {} tras commit: {}", url, ex.getMessage());
            }
        }
    }
}
