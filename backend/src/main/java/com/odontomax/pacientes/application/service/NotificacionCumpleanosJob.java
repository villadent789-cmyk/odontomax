package com.odontomax.pacientes.application.service;

import com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionCumpleanosJob {

    private final ConsultarPacienteUseCase consultarPaciente;

    @Scheduled(cron = "${odontomax.notificaciones.cumpleanos.cron:0 0 8 * * *}", zone = "America/Lima")
    public void detectarCumpleanerosDeManana() {
        int total = consultarPaciente.listarCumpleanerosDeManana(null).size();
        log.info("Cumpleaños de mañana: {} paciente(s)", total);
    }
}
