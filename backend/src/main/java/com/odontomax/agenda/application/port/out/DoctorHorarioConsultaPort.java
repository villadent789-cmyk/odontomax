package com.odontomax.agenda.application.port.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface DoctorHorarioConsultaPort {

    boolean esDoctorActivo(UUID doctorId);

    String obtenerNombreDoctor(UUID doctorId);

    boolean atiendeEnHorarioYSede(UUID doctorId, String sedeId, LocalDate fecha, LocalTime hora, int duracionMinutos);
}
