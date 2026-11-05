package com.odontomax.clinica.persistence;

import com.odontomax.clinica.dto.EpisodioOdontogramaDTO.PiezaInstantanea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "odontograma_episodios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OdontogramaEpisodioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paciente_id", nullable = false)
    private UUID pacienteId;

    @Column(nullable = false)
    private int numero;

    private String motivo;

    @Builder.Default
    @Column(nullable = false)
    private String estado = "ABIERTO";

    @Builder.Default
    @Column(name = "inicial_en", nullable = false)
    private OffsetDateTime inicialEn = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "piezas_iniciales", nullable = false, columnDefinition = "jsonb")
    private List<PiezaInstantanea> inicial;

    @Column(name = "inicial_por")
    private String inicialPor;

    @Column(name = "final_en")
    private OffsetDateTime finalEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "piezas_finales", columnDefinition = "jsonb")
    private List<PiezaInstantanea> finalPiezas;

    @Column(name = "final_por")
    private String finalPor;

    @Column(name = "nota_final")
    private String notaFinal;
}
