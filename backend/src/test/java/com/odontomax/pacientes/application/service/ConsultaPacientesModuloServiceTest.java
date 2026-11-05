package com.odontomax.pacientes.application.service;

import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultaPacientesModuloServiceTest {

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @InjectMocks
    private ConsultaPacientesModuloService service;

    private PacienteEntity paciente(UUID id, String sedeId) {
        return PacienteEntity.builder()
                .id(id)
                .dni("12345678")
                .nombres("Ana")
                .apellidos("Lopez")
                .telefono("987654321")
                .sedeId(sedeId)
                .fechaNacimiento(LocalDate.of(1990, 5, 17))
                .fuenteCaptacion("INSTAGRAM")
                .creadoEn(OffsetDateTime.parse("2026-01-02T03:04:05Z"))
                .totalInasistencias(3)
                .build();
    }

    @Test
    @DisplayName("findById copia todos los campos del resumen")
    void findByIdCopiaCampos() {
        UUID id = UUID.randomUUID();
        when(pacienteRepository.findById(id)).thenReturn(Optional.of(paciente(id, "sede-1")));

        PacienteResumen r = service.findById(id).orElseThrow();

        assertEquals(id, r.id());
        assertEquals("12345678", r.dni());
        assertEquals("Ana", r.nombres());
        assertEquals("Lopez", r.apellidos());
        assertEquals("987654321", r.telefono());
        assertEquals("sede-1", r.sedeId());
        assertEquals(LocalDate.of(1990, 5, 17), r.fechaNacimiento());
        assertEquals("INSTAGRAM", r.fuenteCaptacion());
        assertEquals(OffsetDateTime.parse("2026-01-02T03:04:05Z"), r.creadoEn());
        assertEquals(3, r.totalInasistencias());
    }

    @Test
    @DisplayName("findById devuelve vacío si el paciente no existe")
    void findByIdInexistente() {
        UUID id = UUID.randomUUID();
        when(pacienteRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(service.findById(id).isEmpty());
    }

    @Test
    @DisplayName("findAllById indexa por id y omite los desconocidos")
    void findAllByIdIndexaPorId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID desconocido = UUID.randomUUID();
        Set<UUID> ids = Set.of(a, b, desconocido);
        when(pacienteRepository.findAllById(ids)).thenReturn(List.of(paciente(a, "s1"), paciente(b, "s2")));

        Map<UUID, PacienteResumen> mapa = service.findAllById(ids);

        assertEquals(2, mapa.size());
        assertEquals("s1", mapa.get(a).sedeId());
        assertEquals("s2", mapa.get(b).sedeId());
        assertNull(mapa.get(desconocido));
    }

    @Test
    @DisplayName("listarActivos con sede filtra por esa sede")
    void listarActivosConSede() {
        UUID id = UUID.randomUUID();
        when(pacienteRepository.findActivosBySede("sede-1")).thenReturn(List.of(paciente(id, "sede-1")));

        List<PacienteResumen> lista = service.listarActivos("sede-1");

        assertEquals(1, lista.size());
        assertEquals(id, lista.get(0).id());
        verify(pacienteRepository, never()).findAllActivos(any());
    }

    @Test
    @DisplayName("listarActivos con sede nula devuelve todas las sedes; en blanco filtra por esa sede exacta")
    void listarActivosSinSede() {
        when(pacienteRepository.findAllActivos(null))
                .thenReturn(List.of(paciente(UUID.randomUUID(), "s1"), paciente(UUID.randomUUID(), "s2")));
        when(pacienteRepository.findActivosBySede("  ")).thenReturn(List.of());

        assertEquals(2, service.listarActivos(null).size());
        assertEquals(0, service.listarActivos("  ").size());
        verify(pacienteRepository).findActivosBySede("  ");
    }

    @Test
    @DisplayName("findCumpleanerosDelDia delega la sede tal cual (el puerto normaliza ALL y el blanco)")
    void cumpleanerosDelegaSede() {
        when(pacienteRepository.findCumpleanerosDelDia("all", 5, 17))
                .thenReturn(List.of(paciente(UUID.randomUUID(), "s1")));

        assertEquals(1, service.findCumpleanerosDelDia("all", 5, 17).size());
        verify(pacienteRepository).findCumpleanerosDelDia("all", 5, 17);
    }

    @Test
    @DisplayName("totalInasistencias se expone tal cual el dominio (0 por defecto)")
    void inasistenciasPorDefecto() {
        UUID id = UUID.randomUUID();
        PacienteEntity p = paciente(id, "s1");
        p.setTotalInasistencias(0);
        when(pacienteRepository.findById(id)).thenReturn(Optional.of(p));

        assertEquals(0, service.findById(id).orElseThrow().totalInasistencias());
    }
}
