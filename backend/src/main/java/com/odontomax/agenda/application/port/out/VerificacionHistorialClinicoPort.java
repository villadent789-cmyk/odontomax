package com.odontomax.agenda.application.port.out;

import java.util.UUID;

public interface VerificacionHistorialClinicoPort {
    boolean tieneHistorialClinico(UUID especialistaId);
}
