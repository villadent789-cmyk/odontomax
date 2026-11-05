package com.odontomax.agenda.domain.model;

import com.odontomax.agenda.domain.exception.SedeNoDisponibleException;
import com.odontomax.agenda.domain.exception.SillonInexistenteException;

/**
 * Lo que agenda sabe de una sede para validar citas y especialistas (B-023): si está activa y cuántos sillones tiene.
 */
public record SedeAgenda(String id, boolean activa, int sillones) {

    /** Una cita nueva exige una sede activa y, si indica sillón, que esté entre 1 y el número de sillones de la sede. */
    public void verificarAdmiteCita(Integer sillonNumero) {
        if (!activa) {
            throw new SedeNoDisponibleException("La sede '" + id + "' está inactiva y no admite nuevas citas.");
        }
        if (sillonNumero != null && (sillonNumero < 1 || sillonNumero > sillones)) {
            throw new SillonInexistenteException("El sillón #" + sillonNumero + " no existe en la sede '" + id
                    + "' (sillones del 1 al " + sillones + ").");
        }
    }

    /** Solo se asigna un especialista a una sede activa. */
    public void verificarAdmiteEspecialista() {
        if (!activa) {
            throw new SedeNoDisponibleException("La sede '" + id + "' está inactiva.");
        }
    }
}
