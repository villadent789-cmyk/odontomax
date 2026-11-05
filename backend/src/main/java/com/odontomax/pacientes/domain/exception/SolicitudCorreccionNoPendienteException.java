package com.odontomax.pacientes.domain.exception;

/** La solicitud ya fue aprobada o rechazada: no se puede resolver de nuevo. */
public class SolicitudCorreccionNoPendienteException extends RuntimeException {
    public SolicitudCorreccionNoPendienteException(String message) {
        super(message);
    }
}
