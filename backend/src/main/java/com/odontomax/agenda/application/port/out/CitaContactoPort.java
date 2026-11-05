package com.odontomax.agenda.application.port.out;

import java.util.UUID;

/** Historial de contactos (WhatsApp o llamada) hechos al paciente sobre una cita. */
public interface CitaContactoPort {

    /** Registra un contacto; medio y resultado ya vienen normalizados (mayusculas, sin espacios sobrantes). */
    void registrar(UUID citaId, String medio, String resultado, String notas, String usuario);
}
