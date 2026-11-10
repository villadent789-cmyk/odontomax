package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO;
import com.odontomax.pacientes.application.dto.response.AntecedenteHistorialResponseDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.out.AntecedenteHistorialRepositoryPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import org.springframework.security.access.AccessDeniedException;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.domain.model.AntecedenteHistorial;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AntecedentesApplicationServiceTest {

    @Mock
    private SpringDataPacienteRepository pacienteRepository;

    @Mock
    private AntecedenteHistorialRepositoryPort historialRepository;

    @Mock
    private RegistroAuditoriaPort registroAuditoria;

    @Mock
    private HistorialCitasPort historialCitas;

    @InjectMocks
    private AntecedentesApplicationService service;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String rol, String sede) {
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(
                UUID.randomUUID(), "usuario@odontomax.pe", "Doctor Test", rol, sede);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    @Test
    @DisplayName("obtenerHistorial: falla con 400 si el paciente no existe")
    void obtenerHistorialPacienteInexistente() {
        UUID id = UUID.randomUUID();
        when(pacienteRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerHistorial(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se encontró el paciente con ID: " + id);
    }

    @Test
    @DisplayName("obtenerHistorial: retorna la lista mapeada en orden descendente")
    void obtenerHistorialExitoso() {
        UUID id = UUID.randomUUID();
        autenticar("ADMIN_SEDE", "sede-1");
        PacienteEntity p = PacienteEntity.builder().id(id).sedeId("sede-1").build();
        when(pacienteRepository.findById(id)).thenReturn(Optional.of(p));

        AntecedenteHistorial h = AntecedenteHistorial.builder()
                .id(UUID.randomUUID())
                .pacienteId(id)
                .alergias("Latex")
                .antecedentesMedicos("Asma")
                .gestante(false)
                .motivoCambio("Control")
                .modificadoPorNombre("Dr. Perez")
                .creadoEn(OffsetDateTime.now())
                .build();
        when(historialRepository.findByPacienteIdOrderByCreadoEnDesc(id)).thenReturn(List.of(h));

        List<AntecedenteHistorialResponseDTO> res = service.obtenerHistorial(id);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getAlergias()).isEqualTo("Latex");
        assertThat(res.get(0).getMotivoCambio()).isEqualTo("Control");
    }

    @Test
    @DisplayName("actualizarAntecedentes: falla si el usuario no esta autenticado")
    void actualizarSinAutenticar() {
        UUID id = UUID.randomUUID();
        PacienteEntity p = PacienteEntity.builder().id(id).sedeId("sede-1").build();
        when(pacienteRepository.findById(id)).thenReturn(Optional.of(p));

        AntecedentesUpdateRequestDTO dto = AntecedentesUpdateRequestDTO.builder()
                .alergias("Latex")
                .motivoCambio("Actualizacion")
                .build();

        assertThatThrownBy(() -> service.actualizarAntecedentes(id, dto))
                .isInstanceOf(AccessDeniedException.class);
    }


    @Test
    @DisplayName("actualizarAntecedentes: exito con ODONTOLOGO actualiza paciente, guarda historial con trim, audita y normaliza alergias")
    void actualizarExitoso() {
        UUID id = UUID.randomUUID();
        autenticar("ODONTOLOGO", "sede-1");
        PacienteEntity p = PacienteEntity.builder().id(id).sedeId("sede-1").build();
        when(pacienteRepository.findById(id)).thenReturn(Optional.of(p));
        when(pacienteRepository.save(any(PacienteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AntecedentesUpdateRequestDTO dto = AntecedentesUpdateRequestDTO.builder()
                .alergias("Penicilina, Latex")
                .antecedentesMedicos("Diabetes")
                .gestante(true)
                .semanasGestacion(12)
                .motivoCambio("  Cambio trim  ")
                .build();

        PacienteResponseDTO res = service.actualizarAntecedentes(id, dto);

        // Verifica que se normalicen alergias eliminando espacios alrededor de comas
        assertThat(res.getAlergias()).containsExactly("Penicilina", "Latex");

        // Verifica que el historial tenga el motivo con trim
        ArgumentCaptor<AntecedenteHistorial> histCaptor = ArgumentCaptor.forClass(AntecedenteHistorial.class);
        verify(historialRepository).save(histCaptor.capture());
        assertThat(histCaptor.getValue().getMotivoCambio()).isEqualTo("Cambio trim");

        // Verifica auditoria
        verify(registroAuditoria).registrarEventoClinico(any(), eq("sede-1"), eq("ACTUALIZAR_ANTECEDENTES"),
                eq("antecedentes_historial"), eq(id.toString()), contains("Cambio trim"), any());
    }
    @Test
    @DisplayName("obtenerHistorial: lanza AccessDeniedException cuando odontólogo no tiene cita con el paciente")
    void obtenerHistorial_OdontologoSinCita_Denegado() {
        UUID id = UUID.randomUUID();
        autenticar("ODONTOLOGO", "sede-1");
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitas).exigirAccesoDeOdontologo(id);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.obtenerHistorial(id))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no está asignado");
    }

    @Test
    @DisplayName("actualizarAntecedentes: lanza AccessDeniedException cuando odontólogo no tiene cita con el paciente")
    void actualizarAntecedentes_OdontologoSinCita_Denegado() {
        UUID id = UUID.randomUUID();
        autenticar("ODONTOLOGO", "sede-1");
        doThrow(new AccessDeniedException("Acceso denegado: el paciente no está asignado a este odontólogo."))
                .when(historialCitas).exigirAccesoDeOdontologo(id);

        com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO dto = com.odontomax.pacientes.application.dto.request.AntecedentesUpdateRequestDTO.builder()
                .motivoCambio("Actualizacion")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.actualizarAntecedentes(id, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no está asignado");
    }
}
