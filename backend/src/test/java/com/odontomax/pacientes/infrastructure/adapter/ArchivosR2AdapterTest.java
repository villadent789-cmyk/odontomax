package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.domain.port.out.StoragePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class ArchivosR2AdapterTest {

    private final StoragePort storagePort = mock(StoragePort.class);
    private final ArchivosR2Adapter adapter = new ArchivosR2Adapter(storagePort);

    @Test
    @DisplayName("Sin transacción activa el borrado en R2 es inmediato")
    void borradoInmediatoSinTransaccion() {
        adapter.borrarTrasCommit(List.of("r2/rx/rx1.png"));

        verify(storagePort).deleteFile("r2/rx/rx1.png");
        verifyNoMoreInteractions(storagePort);
    }

    @Test
    @DisplayName("Un fallo al borrar un archivo de R2 no interrumpe el resto")
    void falloEnR2NoInterrumpe() {
        doThrow(new RuntimeException("R2 caído")).when(storagePort).deleteFile("r2/rx/a.png");

        assertDoesNotThrow(() -> adapter.borrarTrasCommit(List.of("r2/rx/a.png", "r2/rx/b.png")));

        verify(storagePort).deleteFile("r2/rx/b.png");
    }
}
