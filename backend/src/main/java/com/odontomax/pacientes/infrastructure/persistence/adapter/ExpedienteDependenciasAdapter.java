package com.odontomax.pacientes.infrastructure.persistence.adapter;

import com.odontomax.domain.model.Radiografia;
import com.odontomax.domain.port.out.RadiografiaRepositoryPort;
import com.odontomax.pacientes.application.port.out.ExpedienteDependenciasPort;
import com.odontomax.pacientes.domain.model.RegistrosProtegidos;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpedienteDependenciasAdapter implements ExpedienteDependenciasPort {

    private final EntityManager entityManager;
    private final RadiografiaRepositoryPort radiografiaRepository;

    @Override
    public RegistrosProtegidos contarRegistrosProtegidos(UUID pacienteId) {
        return new RegistrosProtegidos(
                contar("notas_evolucion", pacienteId),
                contar("antecedentes_historial", pacienteId),
                contar("historia_clinica_accesos", pacienteId),
                contar("pagos", pacienteId),
                contar("consentimientos", pacienteId),
                contar("ordenes_laboratorio", pacienteId));
    }

    @Override
    public List<String> archivosRadiograficos(UUID pacienteId) {
        List<String> archivos = new ArrayList<>();
        List<Radiografia> radiografias = radiografiaRepository.findByPacienteId(pacienteId);
        if (radiografias != null) {
            for (Radiografia rx : radiografias) {
                if (rx.getArchivoUrl() != null && !rx.getArchivoUrl().isBlank()) {
                    archivos.add(rx.getArchivoUrl());
                }
            }
        }
        return archivos;
    }

    @Override
    public void eliminarDependientes(UUID pacienteId) {
        borrar("DELETE FROM radiografias_estudios WHERE paciente_id = :pId", pacienteId);
        // promociones_paciente.cita_id apunta a citas sin cascada: se borran antes (ya caían en cascada con el paciente)
        borrar("DELETE FROM promociones_paciente WHERE paciente_id = :pId", pacienteId);
        borrar("DELETE FROM citas WHERE paciente_id = :pId", pacienteId);
        borrar("DELETE FROM odontograma_registros WHERE paciente_id = :pId", pacienteId);
        borrar("DELETE FROM tratamientos WHERE paciente_id = :pId", pacienteId);
    }

    private long contar(String tabla, UUID pacienteId) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + tabla + " WHERE paciente_id = :pId")
                .setParameter("pId", pacienteId).getSingleResult()).longValue();
    }

    private void borrar(String sql, UUID pacienteId) {
        entityManager.createNativeQuery(sql).setParameter("pId", pacienteId).executeUpdate();
    }
}
