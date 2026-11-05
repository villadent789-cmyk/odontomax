package com.odontomax.pacientes.application.service;

import com.odontomax.application.dto.response.*;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.application.dto.response.*;
import com.odontomax.pacientes.application.port.in.ExportarExpedienteUseCase;
import com.odontomax.pacientes.application.port.in.GestionarAccesoHistoriaUseCase;
import com.odontomax.pacientes.application.port.in.GestionarConsentimientosUseCase;
import com.odontomax.pacientes.application.port.out.ExpedienteSeccionesPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.exception.PacienteNoEncontradoException;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportarExpedienteService implements ExportarExpedienteUseCase {

    private final SpringDataPacienteRepository pacienteRepository;
    private final HistorialCitasPort historialCitasPort;
    private final ExpedienteSeccionesPort expedienteSeccionesPort;
    private final GestionarConsentimientosUseCase consentimientoService;
    private final GestionarAccesoHistoriaUseCase historiaClinicaAccesoService;
    private final RegistroAuditoriaPort registroAuditoria;

    @Override
    @Transactional
    public PacienteExportDTO exportarExpedienteCompleto(UUID pacienteId) {
        historialCitasPort.exigirAccesoDeOdontologo(pacienteId);
        PacienteEntity paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new PacienteNoEncontradoException("Paciente no encontrado"));
        SecurityUtils.validarAccesoSede(paciente.getSedeId());

        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();

        // 1. Datos Personales
        PacienteResponseDTO datosPersonales = PacienteResponseDTO.fromEntity(paciente);
        datosPersonales.setAlergias(paciente.alergiasConEspaciosNormalizados());

        // 2. Citas
        List<CitaResponseDTO> citas = historialCitasPort.citasDePaciente(pacienteId);

        // 3. Tratamientos
        List<TratamientoResponseDTO> tratamientos = expedienteSeccionesPort.listarTratamientosPorPaciente(pacienteId);

        // 4. Pagos y Recibos
        List<PagoResponseDTO> pagos = expedienteSeccionesPort.listarPagosPorPaciente(pacienteId);

        // 5. Notas de evolución médica
        List<NotaEvolucionResponseDTO> notas = expedienteSeccionesPort.listarNotasPorPaciente(pacienteId);

        // 6. Odontograma
        List<OdontogramaRegistroResponseDTO> odontograma = expedienteSeccionesPort.obtenerOdontogramaPorPaciente(pacienteId);

        // 7. Consentimientos informados
        List<ConsentimientoResponseDTO> consentimientos = consentimientoService.listarPorPaciente(pacienteId);

        // 8. Trazabilidad de accesos (Ley 29733)
        List<HistoriaClinicaAccesoResponseDTO> accesos = historiaClinicaAccesoService.listarAccesos(pacienteId);

        // Registrar auditoría de la exportación ARCO
        registroAuditoria.registrarExportacionArco(
                user,
                paciente.getSedeId(),
                pacienteId,
                String.format("Exportación integral de expediente y datos del paciente %s %s conforme a la Ley N° 29733",
                        paciente.getNombres(), paciente.getApellidos()),
                SecurityUtils.getClientIp());

        return PacienteExportDTO.builder()
                .datosPersonales(datosPersonales)
                .citas(citas)
                .tratamientos(tratamientos)
                .pagos(pagos)
                .notasEvolucion(notas)
                .odontograma(odontograma)
                .consentimientos(consentimientos)
                .accesosAuditoria(accesos)
                .generadoEn(OffsetDateTime.now())
                .avisoLegal("Documento clínico y administrativo confidencial expedido en cumplimiento del Derecho ARCO de Acceso (Ley N° 29733 de Protección de Datos Personales de la República del Perú y su Reglamento D.S. 003-2013-JUS). Prohibida su divulgación o uso no autorizado.")
                .build();
    }
}