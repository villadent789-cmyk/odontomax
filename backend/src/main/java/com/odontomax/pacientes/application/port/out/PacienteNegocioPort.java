package com.odontomax.pacientes.application.port.out;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Consultas de pacientes acotadas a un negocio (VILLADENT o VILLAMAX). Son las vistas "todas las sedes" del
 * administrador general: son todas las del negocio con el que está trabajando, nunca las de los dos.
 */
public interface PacienteNegocioPort {

    List<PacienteResponseDTO> listarActivos(String negocio);

    List<PacienteResponseDTO> buscar(String termino, String negocio);

    Page<PacienteResponseDTO> buscarPaginado(String termino, String negocio, Pageable pageable);

    List<PacienteResumen> resumenesActivos(String negocio);

    List<PacienteResumen> cumpleanerosDelDia(String negocio, int mes, int dia);
}
