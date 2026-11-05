package com.odontomax.tratamientos.service;

import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.TratamientoEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.infrastructure.persistence.repository.SpringDataPagoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoItemRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTratamientoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** V23: atender una cita enlazada suma una sesión al plan (TratamientoService.registrarSesionDesdeCita). */
@ExtendWith(MockitoExtension.class)
class TratamientoSesionDesdeCitaTest {

    @Mock private SpringDataTratamientoRepository tratamientoRepository;
    @Mock private SpringDataTratamientoItemRepository itemRepository;
    @Mock private com.odontomax.infrastructure.persistence.repository.SpringDataPlanCuotaRepository cuotaRepository;
    @Mock private ConsultaPacientesModuloApi pacientesApi;
    @Mock private SpringDataPagoRepository pagoRepository;
    @Mock private SpringDataAuditoriaRepository auditoriaRepository;

    @InjectMocks
    private TratamientoService tratamientoService;

    private final UUID tratamientoId = UUID.randomUUID();
    private final UUID citaId = UUID.randomUUID();

    private TratamientoEntity plan(String estado, int realizadas, Integer total) {
        TratamientoEntity t = TratamientoEntity.builder()
                .id(tratamientoId)
                .nombre("Ortodoncia")
                .sedeId("sede-chilca-1")
                .estado(estado)
                .sesionesRealizadas(realizadas)
                .totalSesiones(total)
                .build();
        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(t));
        return t;
    }

    private String detalleAuditado() {
        ArgumentCaptor<AuditoriaLogEntity> captor = ArgumentCaptor.forClass(AuditoriaLogEntity.class);
        verify(auditoriaRepository).save(captor.capture());
        assertEquals("SESION_DESDE_CITA", captor.getValue().getAccion());
        assertEquals("TRATAMIENTOS", captor.getValue().getModulo());
        return captor.getValue().getDetalle();
    }

    @Test
    @DisplayName("En proceso: suma una sesión y lo deja en la auditoría con la cita")
    void sumaSesion() {
        TratamientoEntity t = plan("EN_PROCESO", 2, 5);

        tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId);

        assertEquals(3, t.getSesionesRealizadas());
        assertEquals("EN_PROCESO", t.getEstado());
        verify(tratamientoRepository).save(t);
        String detalle = detalleAuditado();
        assertTrue(detalle.contains("Sesión 3/5"), detalle);
        assertTrue(detalle.contains(citaId.toString()), detalle);
    }

    @Test
    @DisplayName("Planificado: la primera sesión atendida lo pasa a en proceso")
    void planificadoPasaAEnProceso() {
        TratamientoEntity t = plan("PLANIFICADO", 0, 4);

        tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId);

        assertEquals(1, t.getSesionesRealizadas());
        assertEquals("EN_PROCESO", t.getEstado());
    }

    @Test
    @DisplayName("No pasa del total ni finaliza solo: queda registrado que falta finalizarlo")
    void noPasaDelTotal() {
        TratamientoEntity t = plan("EN_PROCESO", 5, 5);

        tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId);

        assertEquals(5, t.getSesionesRealizadas());
        assertEquals("EN_PROCESO", t.getEstado());
        verify(tratamientoRepository, never()).save(any());
        assertTrue(detalleAuditado().contains("falta finalizarlo"));
    }

    @Test
    @DisplayName("Completado o cancelado: no se toca el plan (la cita igual queda atendida)")
    void planCerradoNoCambia() {
        TratamientoEntity t = plan("CANCELADO", 1, 3);

        assertDoesNotThrow(() -> tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId));

        assertEquals(1, t.getSesionesRealizadas());
        verify(tratamientoRepository, never()).save(any());
        assertTrue(detalleAuditado().contains("no se sumó la sesión"));
    }

    @Test
    @DisplayName("Sin total de sesiones definido suma igual")
    void sinTotal() {
        TratamientoEntity t = plan("EN_PROCESO", 1, null);

        tratamientoService.registrarSesionDesdeCita(tratamientoId, citaId);

        assertEquals(2, t.getSesionesRealizadas());
        assertTrue(detalleAuditado().contains("Sesión 2 del plan"));
    }
}
