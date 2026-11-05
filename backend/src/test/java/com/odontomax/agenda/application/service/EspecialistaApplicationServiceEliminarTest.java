package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.exception.EspecialistaConHistorialException;
import com.odontomax.agenda.domain.model.Especialista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EspecialistaApplicationServiceEliminarTest {

    @Mock private EspecialistaRepositoryPort especialistaRepository;
    @Mock private EspecialistaHorarioRepositoryPort horarioRepository;
    @Mock private CitaRepositoryPort citaRepository;
    @Mock private VerificacionHistorialClinicoPort verificacionHistorialClinico;
    @Mock private UsuarioConsultaPort usuarioConsultaPort;
    @Mock private SedeValidacionPort sedeValidacionPort;
    @Mock private RegistroAuditoriaPort auditoriaPort;

    @InjectMocks
    private EspecialistaApplicationService service;

    private UUID espId;
    private Especialista especialista;

    @BeforeEach
    void setUp() {
        espId = UUID.randomUUID();
        especialista = Especialista.builder()
                .id(espId)
                .nombre("Dra. Elena Gómez")
                .especialidad("Endodoncia")
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("Eliminar: sin historial clínico borra horarios, sedes y el registro, y audita")
    void eliminar_sinHistorial_borraFisicamente() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(especialista));
        when(verificacionHistorialClinico.tieneHistorialClinico(espId)).thenReturn(false);

        service.eliminar(espId);

        verify(especialistaRepository).findById(espId);
        verify(verificacionHistorialClinico).tieneHistorialClinico(espId);

        InOrder orden = inOrder(horarioRepository, especialistaRepository);
        orden.verify(horarioRepository).eliminarPorEspecialista(espId);
        orden.verify(especialistaRepository).desvincularTodasLasSedes(espId);
        orden.verify(especialistaRepository).delete(espId);

        ArgumentCaptor<String> detalleCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaPort).registrar(
                eq("CONFIGURACION"),
                eq("ELIMINAR_ESPECIALISTA"),
                eq("especialistas"),
                eq(espId.toString()),
                detalleCaptor.capture()
        );
        assertNotNull(detalleCaptor.getValue());
        assertTrue(detalleCaptor.getValue().contains("Dra. Elena Gómez"));
    }

    @Test
    @DisplayName("Eliminar: con citas o tratamientos responde 409 con el mensaje exacto y no borra nada")
    void eliminar_conHistorial_409() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(especialista));
        when(verificacionHistorialClinico.tieneHistorialClinico(espId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.eliminar(espId));

        assertNotNull(ex);
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(EspecialistaConHistorialException.MENSAJE_DEFECTO, ex.getReason());
        verify(especialistaRepository).findById(espId);
        verify(verificacionHistorialClinico).tieneHistorialClinico(espId);
        verifyNoInteractions(horarioRepository, auditoriaPort);
        verify(especialistaRepository, never()).desvincularTodasLasSedes(any());
        verify(especialistaRepository, never()).delete(any(UUID.class));
    }

    @Test
    @DisplayName("Eliminar: especialista inexistente responde 404")
    void eliminar_noExiste_404() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.eliminar(espId));

        assertNotNull(ex);
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(especialistaRepository).findById(espId);
        verify(verificacionHistorialClinico, never()).tieneHistorialClinico(any());
        verifyNoInteractions(horarioRepository, auditoriaPort);
        verify(especialistaRepository, never()).delete(any(UUID.class));
    }
}
