package com.odontomax.agenda.domain.exception;

public class EspecialistaConHistorialException extends RuntimeException {
    public static final String MENSAJE_DEFECTO = "No se puede eliminar el especialista porque tiene historial clínico registrado. "
            + "Desactívelo en su lugar para mantener la auditoría médica.";

    public EspecialistaConHistorialException() {
        super(MENSAJE_DEFECTO);
    }

    public EspecialistaConHistorialException(String message) {
        super(message);
    }
}
