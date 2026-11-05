package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.port.out.TratamientoCitaPort;
import com.odontomax.application.dto.request.TratamientoCreateRequestDTO;
import com.odontomax.application.dto.request.TratamientoItemRequestDTO;
import com.odontomax.tratamientos.service.TratamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Adaptador hacia el módulo de tratamientos: solo usa su servicio público, nunca sus entidades ni repositorios. */
@Component
@RequiredArgsConstructor
public class TratamientoCrossModuleAdapter implements TratamientoCitaPort {

    private final TratamientoService tratamientoService;

    @Override
    public Optional<TratamientoVinculable> buscar(UUID tratamientoId) {
        return tratamientoService.buscarParaCita(tratamientoId)
                .map(t -> new TratamientoVinculable(t.getId(), t.getPacienteId(), t.getEstado(), t.getNombre()));
    }

    @Override
    public UUID crearPlanParaAdelanto(UUID pacienteId, String sedeId, UUID especialistaId, String motivo, BigDecimal monto) {
        String nombre = motivo != null && !motivo.isBlank() ? motivo.trim() : "Consulta odontológica";
        return tratamientoService.crear(TratamientoCreateRequestDTO.builder()
                .pacienteId(pacienteId)
                .sedeId(sedeId)
                .especialistaId(especialistaId)
                .nombre(nombre)
                .modalidadPago("CONTADO")
                .costoTotal(monto)
                .totalSesiones(1)
                .items(List.of(TratamientoItemRequestDTO.builder()
                        .nombreProcedimiento(nombre)
                        .precioBase(monto)
                        .cantidad(1)
                        .descuento(BigDecimal.ZERO)
                        .build()))
                .build()).getId();
    }

    @Override
    public void validarSaldoParaCobro(UUID pacienteId, UUID tratamientoId, BigDecimal monto) {
        tratamientoService.validarSaldoParaCobro(pacienteId, tratamientoId, monto);
    }

    @Override
    public void registrarSesionAtendida(UUID tratamientoId, UUID citaId) {
        tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId);
    }
}
