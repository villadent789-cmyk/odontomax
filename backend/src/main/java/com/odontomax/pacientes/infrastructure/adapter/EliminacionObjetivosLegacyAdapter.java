package com.odontomax.pacientes.infrastructure.adapter;

import com.odontomax.application.service.RadiografiaService;
import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.pacientes.application.port.out.EliminacionObjetivosPort;
import com.odontomax.pacientes.application.port.out.PacienteIdentidadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Puente hacia el servicio de radiografías, que todavía vive fuera de este módulo. */
@Component
@RequiredArgsConstructor
public class EliminacionObjetivosLegacyAdapter implements EliminacionObjetivosPort {

    private final RadiografiaRepositoryPort radiografias;
    private final RadiografiaService radiografiaService;
    private final PacienteIdentidadPort pacientes;

    @Override
    public Optional<Objetivo> radiografia(UUID radiografiaId) {
        return radiografias.findById(radiografiaId).flatMap(rx -> pacientes.obtener(rx.getPacienteId())
                .map(p -> new Objetivo(rx.getPacienteId(), p.sedeId(), describir(rx, p.nombres() + " " + p.apellidos()))));
    }

    @Override
    public void eliminarRadiografia(UUID radiografiaId, String motivo) {
        radiografiaService.eliminarRadiografia(radiografiaId, motivo);
    }

    private static String describir(Radiografia rx, String paciente) {
        String archivo = rx.getNombreArchivo() == null || rx.getNombreArchivo().isBlank() ? "sin nombre" : rx.getNombreArchivo();
        return "Radiografía " + rx.getTipoEstudio() + " (" + archivo + ", " + rx.getFechaToma() + ") de " + paciente;
    }
}
