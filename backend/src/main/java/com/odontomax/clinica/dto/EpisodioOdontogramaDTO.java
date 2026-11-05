package com.odontomax.clinica.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Un episodio de atención con su odontograma inicial y, si ya terminó, el final. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EpisodioOdontogramaDTO {

    private UUID id;
    private UUID pacienteId;
    /** 1, 2, 3… en orden de llegada del paciente. */
    private int numero;
    private String motivo;
    /** ABIERTO mientras dura el tratamiento; CERRADO tras el alta. */
    private String estado;
    private OffsetDateTime inicialEn;
    private String inicialPor;
    private List<PiezaInstantanea> inicial;
    private OffsetDateTime finalEn;
    private String finalPor;
    private List<PiezaInstantanea> finalPiezas;
    private String notaFinal;

    /** Estado de una pieza en el momento de la captura. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PiezaInstantanea {
        private int piezaFdi;
        private String estado;
        private List<String> caras;
        private String observacion;
    }
}
