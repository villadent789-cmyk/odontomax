package com.odontomax.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaPieza {

    private UUID id;
    private UUID pacienteId;
    private int piezaFdi; // 11-48 (Permanente) o 51-85 (Decidua / Niños)
    private String tipoDenticion; // "PERMANENTE" o "DECIDUA"

    // Caras: VESTIBULAR, PALATINO_LINGUAL, MESIAL, DISTAL, OCLUSAL_INCISAL
    private List<String> carasAfectadas;

    // Diagnóstico o Procedimiento (Caries, Resina, Corona, Endodoncia, Implante, Ausente)
    private String hallazgo;

    // Estado: "POR_HACER" (Rojo) o "REALIZADO" (Azul)
    private String estado;
    private String notas;
    private LocalDate fechaRegistro;
}
