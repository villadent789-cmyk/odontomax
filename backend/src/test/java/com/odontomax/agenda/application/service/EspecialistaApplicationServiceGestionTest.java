package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EspecialistaApplicationServiceGestionTest {

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

    @BeforeEach
    void setUp() {
        espId = UUID.randomUUID();
    }

    private EspecialistaRequestDTO request(List<String> sedes) {
        return EspecialistaRequestDTO.builder()
                .nombre("  Dra. Elena Gómez ")
                .especialidad("Endodoncia")
                .cop("COP-55555")
                .email("elena@odontomax.pe")
                .telefono("987654321")
                .sedes(sedes)
                .build();
    }

    private HorarioEspecialistaRequestDTO bloque(String sede, int dia, String inicio, String fin) {
        return HorarioEspecialistaRequestDTO.builder()
                .sedeId(sede).diaSemana(dia)
                .horaInicio(LocalTime.parse(inicio)).horaFin(LocalTime.parse(fin))
                .build();
    }

    @Test
    @DisplayName("Crear: guarda el especialista, vincula sus sedes y registra auditoría")
    void crear_ok() {
        when(especialistaRepository.existsByCop("COP-55555")).thenReturn(false);
        when(sedeValidacionPort.existeSede(anyString())).thenReturn(true);
        when(especialistaRepository.save(any(Especialista.class))).thenAnswer(inv -> {
            Especialista e = inv.getArgument(0);
            e.setId(espId);
            return e;
        });

        var res = service.crear(request(List.of("sede-chilca-1", "sede-eltambo")));

        assertEquals("Dra. Elena Gómez", res.getNombre());
        verify(especialistaRepository).vincularSede(espId, "sede-chilca-1");
        verify(especialistaRepository).vincularSede(espId, "sede-eltambo");
        verify(auditoriaPort).registrar(
                eq("CONFIGURACION"),
                eq("CREAR_ESPECIALISTA"),
                eq("especialistas"),
                eq(espId.toString()),
                anyString()
        );
    }

    @Test
    @DisplayName("Crear: rechaza COP duplicado sin guardar nada")
    void crear_copDuplicado() {
        when(especialistaRepository.existsByCop("COP-55555")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.crear(request(List.of("sede-chilca-1"))));
        verify(especialistaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: rechaza sede inexistente y lista de sedes vacía")
    void crear_sedesInvalidas() {
        when(especialistaRepository.existsByCop("COP-55555")).thenReturn(false);
        when(sedeValidacionPort.existeSede("sede-fantasma")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.crear(request(List.of("sede-fantasma"))));
        assertThrows(IllegalArgumentException.class, () -> service.crear(request(List.of())));
        verify(especialistaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear: no permite vincular un usuario de Recepción ni uno ya vinculado a otro especialista")
    void crear_usuarioNoVinculable() {
        when(especialistaRepository.existsByCop("COP-55555")).thenReturn(false);
        when(sedeValidacionPort.existeSede(anyString())).thenReturn(true);

        UUID recepcionId = UUID.randomUUID();
        when(usuarioConsultaPort.findById(recepcionId)).thenReturn(Optional.of(
                new UsuarioConsultaPort.UsuarioInfo(recepcionId, "Recep", "recep@odontomax.pe", "RECEPCION", "sede-chilca-1", true)));
        EspecialistaRequestDTO conRecepcion = request(List.of("sede-chilca-1"));
        conRecepcion.setUsuarioId(recepcionId);
        assertThrows(IllegalArgumentException.class, () -> service.crear(conRecepcion));

        UUID doctorId = UUID.randomUUID();
        when(usuarioConsultaPort.findById(doctorId)).thenReturn(Optional.of(
                new UsuarioConsultaPort.UsuarioInfo(doctorId, "Dr. Doc", "doc@odontomax.pe", "ODONTOLOGO", "sede-chilca-1", true)));
        when(especialistaRepository.findByUsuarioId(doctorId)).thenReturn(Optional.of(
                Especialista.builder().id(UUID.randomUUID()).nombre("Dr. Otro").build()));
        EspecialistaRequestDTO conDoctorOcupado = request(List.of("sede-chilca-1"));
        conDoctorOcupado.setUsuarioId(doctorId);
        assertThrows(IllegalArgumentException.class, () -> service.crear(conDoctorOcupado));

        verify(especialistaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar: solo desvincula las sedes quitadas y vincula las nuevas")
    void actualizar_diffDeSedes() {
        Especialista actual = Especialista.builder().id(espId).nombre("X").especialidad("Y").activo(true).build();
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(actual));
        when(especialistaRepository.existsByCopAndIdNot("COP-55555", espId)).thenReturn(false);
        when(sedeValidacionPort.existeSede(anyString())).thenReturn(true);
        when(especialistaRepository.findSedeIdsByEspecialistaId(espId))
                .thenReturn(List.of("sede-chilca-1", "sede-villamax-chilca"));
        when(especialistaRepository.save(any(Especialista.class))).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(espId, request(List.of("sede-chilca-1", "sede-eltambo")));

        verify(especialistaRepository).desvincularSede(espId, "sede-villamax-chilca");
        verify(especialistaRepository, never()).desvincularSede(espId, "sede-chilca-1");
        verify(especialistaRepository).vincularSede(espId, "sede-eltambo");
        verify(especialistaRepository, never()).vincularSede(espId, "sede-chilca-1");
    }

    @Test
    @DisplayName("Horarios: valida sede asignada, hora fin > inicio y un bloque por sede y día")
    void horarios_validaciones() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(
                Especialista.builder().id(espId).nombre("X").especialidad("Y").sedes(List.of("sede-chilca-1")).build()));

        assertThrows(IllegalArgumentException.class,
                () -> service.reemplazarHorarios(espId, List.of(bloque("sede-eltambo", 1, "09:00", "13:00"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.reemplazarHorarios(espId, List.of(bloque("sede-chilca-1", 1, "13:00", "09:00"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.reemplazarHorarios(espId, List.of(
                        bloque("sede-chilca-1", 2, "09:00", "12:00"),
                        bloque("sede-chilca-1", 2, "14:00", "18:00"))));

        verify(horarioRepository, never()).eliminarPorEspecialista(any());
        verify(horarioRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Horarios: reemplaza el horario completo y audita")
    void horarios_reemplazo() {
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(
                Especialista.builder().id(espId).nombre("X").especialidad("Y").sedes(List.of("sede-chilca-1")).build()));
        when(especialistaRepository.existsById(espId)).thenReturn(true);

        service.reemplazarHorarios(espId, List.of(
                bloque("sede-chilca-1", 1, "09:00", "13:00"),
                bloque("sede-chilca-1", 3, "15:00", "19:00")));

        verify(horarioRepository).eliminarPorEspecialista(espId);
        verify(horarioRepository).saveAll(argThat(lista -> ((List<?>) lista).size() == 2));
        verify(auditoriaPort).registrar(
                eq("CONFIGURACION"),
                eq("ACTUALIZAR_HORARIO_ESPECIALISTA"),
                eq("especialistas"),
                eq(espId.toString()),
                anyString()
        );
    }

    @Test
    @DisplayName("Sincronización: cuenta nueva sin doctor previo crea perfil y horario habitual")
    void asegurarPerfil_creaNuevo() {
        UUID usuarioId = UUID.randomUUID();
        when(especialistaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());
        when(especialistaRepository.findFirstByEmailIgnoreCaseAndUsuarioIdIsNull("ana.rios@odontomax.pe")).thenReturn(Optional.empty());
        when(sedeValidacionPort.existeSede("sede-chilca-1")).thenReturn(true);
        when(usuarioConsultaPort.findById(usuarioId)).thenReturn(Optional.of(
                new UsuarioConsultaPort.UsuarioInfo(usuarioId, "Dra. Ana Ríos", "ana.rios@odontomax.pe", "ODONTOLOGO", "sede-chilca-1", true)));
        when(especialistaRepository.save(any(Especialista.class))).thenAnswer(inv -> {
            Especialista e = inv.getArgument(0);
            e.setId(espId);
            return e;
        });
        when(especialistaRepository.findById(espId)).thenAnswer(inv -> Optional.of(
                Especialista.builder().id(espId).nombre("Dra. Ana Ríos").especialidad("Odontología General").sedes(List.of("sede-chilca-1")).activo(true).build()));
        when(especialistaRepository.existsById(espId)).thenReturn(true);

        var dto = service.asegurarPerfilDeOdontologo(usuarioId, "Dra. Ana Ríos", "ana.rios@odontomax.pe", "sede-chilca-1");

        assertEquals(espId, dto.getId());
        ArgumentCaptor<Especialista> creado = ArgumentCaptor.forClass(Especialista.class);
        verify(especialistaRepository, atLeastOnce()).save(creado.capture());
        verify(especialistaRepository).vincularSede(espId, "sede-chilca-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HorarioEspecialista>> horarios = ArgumentCaptor.forClass(List.class);
        verify(horarioRepository).saveAll(horarios.capture());
        // Horario por defecto de Villadent (V24): lunes a domingo de 09:00 a 20:00.
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), horarios.getValue().stream().map(HorarioEspecialista::getDiaSemana).toList());
        assertTrue(horarios.getValue().stream().allMatch(h ->
                h.getHoraInicio().equals(java.time.LocalTime.of(9, 0)) && h.getHoraFin().equals(java.time.LocalTime.of(20, 0))));
    }

    @Test
    @DisplayName("Sincronización: doctor manual con mismo correo se vincula sin duplicar")
    void asegurarPerfil_vinculaExistentePorCorreo() {
        UUID usuarioId = UUID.randomUUID();
        Especialista existente = Especialista.builder().id(espId).nombre("Dra. Ana Ríos")
                .especialidad("Ortodoncia").email("ana.rios@odontomax.pe").activo(true).build();
        when(especialistaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());
        when(especialistaRepository.findFirstByEmailIgnoreCaseAndUsuarioIdIsNull("ana.rios@odontomax.pe")).thenReturn(Optional.of(existente));
        when(especialistaRepository.findSedeIdsByEspecialistaId(espId)).thenReturn(List.of("sede-eltambo"));
        when(especialistaRepository.save(any(Especialista.class))).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.asegurarPerfilDeOdontologo(usuarioId, "Dra. Ana Ríos", "ana.rios@odontomax.pe", "sede-chilca-1");

        assertEquals(usuarioId, existente.getUsuarioId());
        assertEquals("Ortodoncia", dto.getEspecialidad());
        verify(especialistaRepository).vincularSede(espId, "sede-chilca-1");
        verify(horarioRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Sincronización: cuenta ya vinculada no modifica nada")
    void asegurarPerfil_noDuplica() {
        UUID usuarioId = UUID.randomUUID();
        when(especialistaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(
                Especialista.builder().id(espId).nombre("Dra. Ana").especialidad("General")
                        .email("ana@odontomax.pe").activo(true).build()));
        when(especialistaRepository.findSedeIdsByEspecialistaId(espId)).thenReturn(List.of("sede-chilca-1"));

        var dto = service.asegurarPerfilDeOdontologo(usuarioId, "Dra. Ana", "ana@odontomax.pe", "sede-chilca-1");

        assertEquals(espId, dto.getId());
        verify(especialistaRepository, never()).save(any());
        verify(especialistaRepository, never()).vincularSede(any(), any());
        verify(horarioRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Sincronización: desactivar la cuenta desactiva al doctor; si ya coincide no hace nada")
    void sincronizaEstado() {
        UUID usuarioId = UUID.randomUUID();
        Especialista esp = Especialista.builder().id(espId).nombre("Dra. Ana").especialidad("General").activo(true).build();
        when(especialistaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(esp));
        when(especialistaRepository.findById(espId)).thenReturn(Optional.of(esp));
        when(especialistaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sincronizarEstadoConUsuario(usuarioId, false);
        assertFalse(esp.isActivo());
        verify(especialistaRepository).save(esp);

        clearInvocations(especialistaRepository);
        service.sincronizarEstadoConUsuario(usuarioId, false);
        verify(especialistaRepository, never()).save(any());
    }
}
