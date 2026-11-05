package com.odontomax.pacientes.application.service;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
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

@ExtendWith(MockitoExtension.class)
class RegistroPacientesModuloServiceTest {

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @InjectMocks
    private RegistroPacientesModuloService service;

    @Test
    @DisplayName("paciente nuevo: se crea con datos recortados, no archivado y sin saldo")
    void creaPacienteNuevo() {
        when(pacienteRepository.findByDni("12345678")).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(PacienteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PacienteResumen r = service.obtenerOCrearPorDni(" 12345678 ", " Ana ", " Lopez ", " 987654321 ", " sede-1 ");

        ArgumentCaptor<PacienteEntity> captor = ArgumentCaptor.forClass(PacienteEntity.class);
        verify(pacienteRepository).save(captor.capture());
        PacienteEntity guardado = captor.getValue();
        assertNotNull(guardado.getId());
        assertEquals("12345678", guardado.getDni());
        assertEquals("Ana", guardado.getNombres());
        assertEquals("Lopez", guardado.getApellidos());
        assertEquals("987654321", guardado.getTelefono());
        assertEquals("sede-1", guardado.getSedeId());
        assertFalse(guardado.getArchivado() != null && guardado.getArchivado());
        assertNull(guardado.getSaldoPendiente());
        assertEquals(guardado.getId(), r.id());
        assertEquals("Ana", r.nombres());
        assertEquals("Lopez", r.apellidos());
        assertEquals("987654321", r.telefono());
    }

    @Test
    @DisplayName("paciente existente sin teléfono: se completa el teléfono y se guarda")
    void completaTelefonoFaltante() {
        UUID id = UUID.randomUUID();
        PacienteEntity existente = PacienteEntity.builder().id(id).dni("12345678").nombres("Ana").apellidos("Lopez").sedeId("s1").build();
        when(pacienteRepository.findByDni("12345678")).thenReturn(Optional.of(existente));
        when(pacienteRepository.save(existente)).thenReturn(existente);

        PacienteResumen r = service.obtenerOCrearPorDni("12345678", "X", "Y", " 999888777 ", "s2");

        verify(pacienteRepository).save(existente);
        assertEquals("999888777", r.telefono());
        assertEquals(id, r.id());
        assertEquals("s1", r.sedeId());
    }

    @Test
    @DisplayName("paciente existente con teléfono: no se guarda nada")
    void existenteConTelefonoNoSeGuarda() {
        UUID id = UUID.randomUUID();
        PacienteEntity existente = PacienteEntity.builder().id(id).dni("12345678").nombres("Ana").apellidos("Lopez")
                .telefono("911222333").sedeId("s1").build();
        when(pacienteRepository.findByDni("12345678")).thenReturn(Optional.of(existente));

        PacienteResumen r = service.obtenerOCrearPorDni("12345678", "X", "Y", "999888777", "s2");

        verify(pacienteRepository, never()).save(any());
        assertEquals("911222333", r.telefono());
    }
}
