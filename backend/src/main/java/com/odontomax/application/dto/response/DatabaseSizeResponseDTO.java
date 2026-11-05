package com.odontomax.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseSizeResponseDTO {
    private long tamanoBytes;
    private double tamanoMb;
    private long tamanoPublicBytes;
    private double tamanoPublicMb;
    private double cuotaMaximaMb;
    private double porcentajeUso;
    private String alerta; // NORMAL (<70%), ADVERTENCIA (>=70% y <85%), CRITICA (>=85%)
    private String mensaje;
    private List<TablaSizeDTO> topTablas;
}
