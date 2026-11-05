package com.odontomax.sedes.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ServicioResponseDTO(
        String id,
        String nombre,
        String especialidad,
        String descripcion,
        Integer duracionMin,
        BigDecimal precio,
        boolean activo,
        Integer orden,
        BigDecimal comisionPorcentaje,
        BigDecimal comisionFija,
        BigDecimal precioLeve,
        BigDecimal precioModerado,
        BigDecimal precioSevero
) {
    public String getId() { return id(); }
    public String getNombre() { return nombre(); }
    public String getEspecialidad() { return especialidad(); }
    public String getDescripcion() { return descripcion(); }
    public Integer getDuracionMin() { return duracionMin(); }
    public BigDecimal getPrecio() { return precio(); }
    public boolean isActivo() { return activo(); }
    public Integer getOrden() { return orden(); }
    public BigDecimal getComisionPorcentaje() { return comisionPorcentaje(); }
    public BigDecimal getComisionFija() { return comisionFija(); }
    public BigDecimal getPrecioLeve() { return precioLeve(); }
    public BigDecimal getPrecioModerado() { return precioModerado(); }
    public BigDecimal getPrecioSevero() { return precioSevero(); }
}
