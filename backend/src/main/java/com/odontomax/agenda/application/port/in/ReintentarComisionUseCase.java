package com.odontomax.agenda.application.port.in;

import java.util.UUID;

public interface ReintentarComisionUseCase {

    /** Vuelve a intentar generar la comisión de un estudio cobrado cuya comisión falló la primera vez. */
    void reintentar(UUID citaId);
}
