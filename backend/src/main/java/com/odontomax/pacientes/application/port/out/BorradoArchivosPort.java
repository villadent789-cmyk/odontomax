package com.odontomax.pacientes.application.port.out;

import java.util.List;

public interface BorradoArchivosPort {

    /**
     * Borra los archivos del almacenamiento recién después de confirmar la transacción en curso (inmediatamente si no hay
     * ninguna). Un fallo con un archivo se registra y no interrumpe el resto.
     */
    void borrarTrasCommit(List<String> urls);
}
