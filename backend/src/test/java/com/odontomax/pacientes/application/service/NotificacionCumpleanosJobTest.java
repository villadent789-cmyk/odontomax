package com.odontomax.pacientes.application.service;

import com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionCumpleanosJobTest {

    @Mock
    private ConsultarPacienteUseCase consultarPaciente;

    @InjectMocks
    private NotificacionCumpleanosJob job;

    @Test
    @DisplayName("El job diario consulta los cumpleañeros de mañana en todas las sedes")
    void consultaTodasLasSedes() {
        when(consultarPaciente.listarCumpleanerosDeManana(null)).thenReturn(List.of());

        assertDoesNotThrow(job::detectarCumpleanerosDeManana);

        verify(consultarPaciente).listarCumpleanerosDeManana(null);
    }
}
