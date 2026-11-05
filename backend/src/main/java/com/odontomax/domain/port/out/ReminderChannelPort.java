package com.odontomax.domain.port.out;

import com.odontomax.domain.model.Cita;
import com.odontomax.pacientes.domain.model.Paciente;

public interface ReminderChannelPort {

    /**
     * Envía un recordatorio automatizado al paciente sobre su cita programada.
     * @return true si el recordatorio fue despachado o registrado satisfactoriamente.
     */
    boolean enviarRecordatorio(Cita cita, Paciente paciente);
}
