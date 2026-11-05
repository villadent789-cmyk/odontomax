package com.odontomax.pacientes.application.port.in;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GestionarFotoPacienteUseCase {

    record FotoPaciente(byte[] contenido, String contentType, OffsetDateTime actualizadaEn) {
    }

    Optional<FotoPaciente> obtener(UUID pacienteId);

    /** Guarda o reemplaza la foto. Solo imágenes JPEG, PNG o WebP de hasta 2 MB, validadas por su contenido real. */
    OffsetDateTime guardar(UUID pacienteId, byte[] contenido, String contentType);

    void eliminar(UUID pacienteId);
}
