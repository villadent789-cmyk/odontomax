package com.odontomax.agenda.domain.exception;

public class SillonNoAsignadoException extends RuntimeException {
    public SillonNoAsignadoException() {
        super("El doctor no tiene sillón asignado en esta sede; asígnalo en Administración");
    }
}
