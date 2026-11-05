package com.odontomax.sedes.dto;

import com.odontomax.sedes.model.UnidadDeNegocio;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Builder
public record SedeResponseDTO(
        String id,
        String nombre,
        String direccion,
        String ciudad,
        String telefono,
        String whatsapp,
        String email,
        String horarioTexto,
        String color,
        String colorSecundario,
        Integer sillones,
        String descripcion,
        String fotoPortada,
        List<String> fotos,
        BigDecimal latitud,
        BigDecimal longitud,
        boolean activa,
        UnidadDeNegocio unidadDeNegocio,
        String ruc,
        BigDecimal montoAdelantoDefecto,
        Integer horasExpiracionAdelanto,
        Integer horasAnticipacionCancelacion,
        Integer horasRecordatorioWhatsapp,
        LocalTime horaInicioLlamadas,
        Integer diasVigenciaPromocionCumpleanos
) {
    public String getId() { return id(); }
    public String getNombre() { return nombre(); }
    public String getDireccion() { return direccion(); }
    public String getCiudad() { return ciudad(); }
    public String getTelefono() { return telefono(); }
    public String getWhatsapp() { return whatsapp(); }
    public String getEmail() { return email(); }
    public String getHorarioTexto() { return horarioTexto(); }
    public String getColor() { return color(); }
    public String getColorSecundario() { return colorSecundario(); }
    public Integer getSillones() { return sillones(); }
    public String getDescripcion() { return descripcion(); }
    public String getFotoPortada() { return fotoPortada(); }
    public List<String> getFotos() { return fotos(); }
    public BigDecimal getLatitud() { return latitud(); }
    public BigDecimal getLongitud() { return longitud(); }
    public boolean isActiva() { return activa(); }
    public UnidadDeNegocio getUnidadDeNegocio() { return unidadDeNegocio(); }
    public String getRuc() { return ruc(); }
    public BigDecimal getMontoAdelantoDefecto() { return montoAdelantoDefecto(); }
    public Integer getHorasExpiracionAdelanto() { return horasExpiracionAdelanto(); }
    public Integer getHorasAnticipacionCancelacion() { return horasAnticipacionCancelacion(); }
    public Integer getHorasRecordatorioWhatsapp() { return horasRecordatorioWhatsapp(); }
    public LocalTime getHoraInicioLlamadas() { return horaInicioLlamadas(); }
    public Integer getDiasVigenciaPromocionCumpleanos() { return diasVigenciaPromocionCumpleanos(); }
}
