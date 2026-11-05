package com.odontomax.pacientes.application.dto.response;

import com.odontomax.application.dto.response.CitaResponseDTO;
import com.odontomax.application.dto.response.NotaEvolucionResponseDTO;
import com.odontomax.application.dto.response.OdontogramaRegistroResponseDTO;
import com.odontomax.application.dto.response.PagoResponseDTO;
import com.odontomax.application.dto.response.TratamientoResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteExportDTO {

    private PacienteResponseDTO datosPersonales;
    private List<CitaResponseDTO> citas;
    private List<TratamientoResponseDTO> tratamientos;
    private List<PagoResponseDTO> pagos;
    private List<NotaEvolucionResponseDTO> notasEvolucion;
    private List<OdontogramaRegistroResponseDTO> odontograma;
    private List<ConsentimientoResponseDTO> consentimientos;
    private List<HistoriaClinicaAccesoResponseDTO> accesosAuditoria;
    private OffsetDateTime generadoEn;
    private String avisoLegal;
}
