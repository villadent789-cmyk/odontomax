package com.odontomax.pacientes.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.pacientes.application.dto.request.FichaIngresoDTO;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.FichaIngresoEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.FotoPacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataFichaIngresoRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataFotoPacienteRepository;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FichaIngresoYFotoServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};

    @Mock private SpringDataPacienteRepository pacienteRepository;
    @Mock private SpringDataFichaIngresoRepository fichaRepository;
    @Mock private SpringDataFotoPacienteRepository fotoRepository;
    @Mock private HistorialCitasPort historialCitas;
    @Mock private RegistroAuditoriaPort registroAuditoria;

    private FichaIngresoApplicationService fichaService;
    private FotoPacienteApplicationService fotoService;
    private final UUID pacienteId = UUID.randomUUID();
    private PacienteEntity paciente;

    @BeforeEach
    void preparar() {
        fichaService = new FichaIngresoApplicationService(pacienteRepository, fichaRepository, historialCitas, registroAuditoria);
        fotoService = new FotoPacienteApplicationService(pacienteRepository, fotoRepository, historialCitas, registroAuditoria);
        paciente = PacienteEntity.builder().id(pacienteId).sedeId("sede-1").build();
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente));
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(UUID.randomUUID(), "recepcion@odontomax.pe", "Katherin", "RECEPCION", "sede-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_RECEPCION"))));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Un paciente sin ficha devuelve una ficha vacía, no un error")
    void fichaVacia() {
        when(fichaRepository.findById(pacienteId)).thenReturn(Optional.empty());

        assertThat(fichaService.obtener(pacienteId)).isEqualTo(new FichaIngresoDTO());
    }

    @Test
    @DisplayName("Guardar la ficha pone la fecha y el autor desde el servidor y deja auditoría")
    void guardarFicha() {
        when(fichaRepository.findById(pacienteId)).thenReturn(Optional.empty());
        when(fichaRepository.save(any(FichaIngresoEntity.class))).thenAnswer(i -> i.getArgument(0));
        FichaIngresoDTO enviada = FichaIngresoDTO.builder()
                .emergenciaNombre("Rosa Quispe").emergenciaTelefono("964123456").emergenciaParentesco("Madre")
                .bruxismo(true).actualizadoPorNombre("Otra persona").build();

        FichaIngresoDTO guardada = fichaService.guardar(pacienteId, enviada);

        assertThat(guardada.getEmergenciaParentesco()).isEqualTo("Madre");
        assertThat(guardada.getBruxismo()).isTrue();
        assertThat(guardada.getActualizadoPorNombre()).isEqualTo("Katherin");
        assertThat(guardada.getActualizadoEn()).isNotNull();
        verify(registroAuditoria).registrarEventoClinico(any(), org.mockito.ArgumentMatchers.eq("sede-1"),
                org.mockito.ArgumentMatchers.eq("GUARDAR_FICHA_INGRESO"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La foto se acepta por su contenido real: JPEG, PNG o WebP")
    void fotoPorContenido() {
        assertThat(FotoPacienteApplicationService.tipoSegunContenido(JPEG)).isEqualTo("image/jpeg");
        assertThat(FotoPacienteApplicationService.tipoSegunContenido(PNG)).isEqualTo("image/png");
        assertThat(FotoPacienteApplicationService.tipoSegunContenido(WEBP)).isEqualTo("image/webp");
        assertThat(FotoPacienteApplicationService.tipoSegunContenido("<script>alert(1)</script>".getBytes())).isNull();
    }

    @Test
    @DisplayName("Subir una foto la guarda y marca al paciente con su fecha")
    void subirFoto() {
        when(fotoRepository.findById(pacienteId)).thenReturn(Optional.empty());

        var fecha = fotoService.guardar(pacienteId, JPEG, "image/png");

        ArgumentCaptor<FotoPacienteEntity> captor = ArgumentCaptor.forClass(FotoPacienteEntity.class);
        verify(fotoRepository).save(captor.capture());
        // El tipo guardado es el real (JPEG), no el que declaró el cliente.
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(paciente.getFotoActualizadaEn()).isEqualTo(fecha);
    }

    @Test
    @DisplayName("Un archivo que no es imagen o pesa más de 2 MB se rechaza")
    void fotoInvalida() {
        assertThatThrownBy(() -> fotoService.guardar(pacienteId, "no soy una foto".getBytes(), "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG, PNG o WebP");
        byte[] enorme = new byte[FotoPacienteApplicationService.MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, enorme, 0, JPEG.length);
        assertThatThrownBy(() -> fotoService.guardar(pacienteId, enorme, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 MB");
        verify(fotoRepository, never()).save(any());
    }
}
