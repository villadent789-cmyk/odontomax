package com.odontomax.agenda.application.service;

import com.odontomax.agenda.application.dto.request.EspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.MetaDoctorRequestDTO;
import com.odontomax.agenda.application.dto.response.MetaDoctorResponseDTO;
import com.odontomax.agenda.application.dto.request.HorarioEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.request.SillonesEspecialistaRequestDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadResponseDTO;
import com.odontomax.agenda.application.dto.response.DisponibilidadSlotDTO;
import com.odontomax.agenda.application.dto.response.EspecialistaResponseDTO;
import com.odontomax.agenda.application.dto.response.HorarioEspecialistaResponseDTO;
import com.odontomax.agenda.application.port.in.ConsultarDisponibilidadUseCase;
import com.odontomax.agenda.application.port.in.ConsultarEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionEspecialistaUseCase;
import com.odontomax.agenda.application.port.in.GestionHorariosEspecialistaUseCase;
import com.odontomax.agenda.application.port.out.RegistroAuditoriaPort;
import com.odontomax.agenda.application.port.out.CitaRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaHorarioRepositoryPort;
import com.odontomax.agenda.application.port.out.EspecialistaRepositoryPort;
import com.odontomax.agenda.application.port.out.SedeValidacionPort;
import com.odontomax.agenda.application.port.out.UsuarioConsultaPort;
import com.odontomax.agenda.application.port.out.VerificacionHistorialClinicoPort;
import com.odontomax.agenda.domain.exception.EspecialistaConHistorialException;
import com.odontomax.agenda.domain.exception.SillonEspecialistaFijoException;
import com.odontomax.agenda.domain.model.Cita;
import com.odontomax.agenda.domain.model.Especialista;
import com.odontomax.agenda.domain.model.SedeAgenda;
import com.odontomax.agenda.domain.model.HorarioEspecialista;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EspecialistaApplicationService implements
        GestionEspecialistaUseCase,
        ConsultarEspecialistaUseCase,
        GestionHorariosEspecialistaUseCase,
        ConsultarDisponibilidadUseCase {

    private final EspecialistaRepositoryPort especialistaRepository;
    private final EspecialistaHorarioRepositoryPort especialistaHorarioRepository;
    private final CitaRepositoryPort citaRepository;
    private final VerificacionHistorialClinicoPort verificacionHistorialClinico;
    private final UsuarioConsultaPort usuarioConsultaPort;
    private final SedeValidacionPort sedeValidacionPort;
    private final RegistroAuditoriaPort auditoriaPort;

    /** Reloj de la clínica (hora de Lima); no forma parte del constructor, los tests lo fijan con {@link #setReloj}. */
    private Clock reloj = Clock.system(CitaApplicationService.ZONE_LIMA);

    void setReloj(Clock reloj) {
        this.reloj = reloj;
    }

    private EspecialistaResponseDTO toDTO(Especialista e) {
        return EspecialistaResponseDTO.builder()
                .id(e.getId())
                .usuarioId(e.getUsuarioId())
                .nombre(e.getNombre())
                .especialidad(e.getEspecialidad())
                .cop(e.getCop())
                .email(e.getEmail())
                .telefono(e.getTelefono())
                .fotoUrl(e.getFotoUrl())
                .activo(e.isActivo())
                .sedes(e.getSedes())
                .sillonesPorSede(e.getSillonesPorSede())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecialistaResponseDTO> listarTodos() {
        return especialistaRepository.findAllByOrderByNombreAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecialistaResponseDTO> listarEspecialistas(String sedeId) {
        List<Especialista> especialistas;
        if (sedeId != null && !sedeId.isBlank()) {
            especialistas = especialistaRepository.findBySedeIdAndActivoTrue(sedeId.trim());
        } else {
            // "Todas las sedes" del administrador general = todas las del negocio activo (Villadent o Villamax).
            List<String> sedesDelNegocio = com.odontomax.identidad.api.SecurityUtils.sedesDelNegocioActivo();
            especialistas = especialistaRepository.findByActivoTrueOrderByNombreAsc().stream()
                    .filter(e -> sedesDelNegocio == null
                            || e.getSedes().stream().anyMatch(sedesDelNegocio::contains))
                    .toList();
        }
        return especialistas.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecialistaResponseDTO> listarPublicos() {
        return especialistaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(e -> EspecialistaResponseDTO.builder()
                        .id(e.getId())
                        .nombre(e.getNombre())
                        .especialidad(e.getEspecialidad())
                        .fotoUrl(e.getFotoUrl())
                        .activo(true)
                        .sedes(e.getSedes())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EspecialistaResponseDTO> listarPublicosPorSede(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return listarPublicos();
        }
        return especialistaRepository.findBySedeIdAndActivoTrue(sedeId.trim()).stream()
                .map(e -> EspecialistaResponseDTO.builder()
                        .id(e.getId())
                        .nombre(e.getNombre())
                        .especialidad(e.getEspecialidad())
                        .fotoUrl(e.getFotoUrl())
                        .activo(true)
                        .sedes(e.getSedes())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public EspecialistaResponseDTO crear(EspecialistaRequestDTO dto) {
        return crear(dto, true);
    }

    /**
     * @param exigirSedesActivas B-023: el alta por {@code POST /especialistas} exige sedes activas; el perfil que se
     *                           crea al registrar un usuario ODONTOLOGO (identidad) solo exige que la sede exista.
     */
    private EspecialistaResponseDTO crear(EspecialistaRequestDTO dto, boolean exigirSedesActivas) {
        String cop = normalizarOpcional(dto.getCop());
        if (cop != null && especialistaRepository.existsByCop(cop)) {
            throw new IllegalArgumentException("Ya existe un especialista registrado con el COP " + cop + ".");
        }
        Set<String> sedes = validarSedes(dto.getSedes());
        if (exigirSedesActivas) {
            sedes.forEach(this::verificarSedeAdmiteEspecialista);
        }
        validarUsuarioVinculable(dto.getUsuarioId(), null);

        Especialista esp = Especialista.builder()
                .nombre(dto.getNombre().trim())
                .especialidad(dto.getEspecialidad().trim())
                .cop(cop)
                .email(normalizarOpcional(dto.getEmail()))
                .telefono(normalizarOpcional(dto.getTelefono()))
                .fotoUrl(normalizarOpcional(dto.getFotoUrl()))
                .usuarioId(dto.getUsuarioId())
                .activo(true)
                .sedes(new ArrayList<>(sedes))
                .build();

        Especialista guardado = especialistaRepository.save(esp);
        sedes.forEach(sedeId -> especialistaRepository.vincularSede(guardado.getId(), sedeId));
        guardado.setSedes(new ArrayList<>(sedes));

        auditoriaPort.registrar(
                "CONFIGURACION",
                "CREAR_ESPECIALISTA",
                "especialistas",
                guardado.getId().toString(),
                String.format("Especialista creado: %s (%s) | Sedes: %s", guardado.getNombre(), guardado.getEspecialidad(), sedes)
        );

        return toDTO(guardado);
    }

    @Override
    @Transactional
    public EspecialistaResponseDTO actualizar(UUID id, EspecialistaRequestDTO dto) {
        Especialista esp = especialistaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado con ID: " + id));

        String cop = normalizarOpcional(dto.getCop());
        if (cop != null && especialistaRepository.existsByCopAndIdNot(cop, id)) {
            throw new IllegalArgumentException("Ya existe otro especialista registrado con el COP " + cop + ".");
        }
        Set<String> sedesNuevas = validarSedes(dto.getSedes());
        validarUsuarioVinculable(dto.getUsuarioId(), id);

        esp.setNombre(dto.getNombre().trim());
        esp.setEspecialidad(dto.getEspecialidad().trim());
        esp.setCop(cop);
        esp.setEmail(normalizarOpcional(dto.getEmail()));
        esp.setTelefono(normalizarOpcional(dto.getTelefono()));
        esp.setFotoUrl(normalizarOpcional(dto.getFotoUrl()));
        esp.setUsuarioId(dto.getUsuarioId());
        Especialista actualizado = especialistaRepository.save(esp);

        Set<String> sedesActuales = new HashSet<>(especialistaRepository.findSedeIdsByEspecialistaId(id));
        // B-023: solo las sedes que se añaden deben estar activas (las ya asignadas se conservan aunque se desactiven)
        sedesNuevas.stream().filter(s -> !sedesActuales.contains(s)).forEach(this::verificarSedeAdmiteEspecialista);
        sedesActuales.stream().filter(s -> !sedesNuevas.contains(s))
                .forEach(s -> especialistaRepository.desvincularSede(id, s));
        sedesNuevas.stream().filter(s -> !sedesActuales.contains(s))
                .forEach(s -> especialistaRepository.vincularSede(id, s));

        actualizado.setSedes(new ArrayList<>(sedesNuevas));

        auditoriaPort.registrar(
                "CONFIGURACION",
                "EDITAR_ESPECIALISTA",
                "especialistas",
                id.toString(),
                String.format("Especialista actualizado: %s (%s) | Sedes: %s", actualizado.getNombre(), actualizado.getEspecialidad(), sedesNuevas)
        );

        return toDTO(actualizado);
    }

    @Override
    @Transactional
    public EspecialistaResponseDTO cambiarEstado(UUID id, boolean activo) {
        Especialista esp = especialistaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado con ID: " + id));
        if (activo) {
            esp.activar();
        } else {
            esp.desactivar();
        }
        Especialista actualizado = especialistaRepository.save(esp);

        auditoriaPort.registrar(
                "CONFIGURACION",
                activo ? "ACTIVAR_ESPECIALISTA" : "DESACTIVAR_ESPECIALISTA",
                "especialistas",
                id.toString(),
                String.format("Especialista %s marcado como %s", esp.getNombre(), activo ? "ACTIVO" : "INACTIVO")
        );

        return toDTO(actualizado);
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        Especialista esp = especialistaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Especialista no encontrado con ID: " + id));

        if (verificacionHistorialClinico.tieneHistorialClinico(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, EspecialistaConHistorialException.MENSAJE_DEFECTO);
        }

        especialistaHorarioRepository.eliminarPorEspecialista(id);
        especialistaRepository.desvincularTodasLasSedes(id);
        especialistaRepository.delete(id);

        auditoriaPort.registrar(
                "CONFIGURACION",
                "ELIMINAR_ESPECIALISTA",
                "especialistas",
                id.toString(),
                String.format("Especialista eliminado físicamente: %s (%s)", esp.getNombre(), esp.getEspecialidad())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorarioEspecialistaResponseDTO> obtenerHorarios(UUID id) {
        if (!especialistaRepository.existsById(id)) {
            throw new IllegalArgumentException("Especialista no encontrado con ID: " + id);
        }
        return especialistaHorarioRepository.findByEspecialistaId(id).stream()
                .map(h -> HorarioEspecialistaResponseDTO.builder()
                        .id(h.getId())
                        .sedeId(h.getSedeId())
                        .diaSemana(h.getDiaSemana())
                        .horaInicio(h.getHoraInicio())
                        .horaFin(h.getHoraFin())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public List<HorarioEspecialistaResponseDTO> reemplazarHorarios(
            UUID id, List<HorarioEspecialistaRequestDTO> horarios) {
        Especialista esp = especialistaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado con ID: " + id));

        List<HorarioEspecialistaRequestDTO> lista = horarios != null ? horarios : Collections.emptyList();

        List<HorarioEspecialista> dominioHorarios = lista.stream()
                .map(h -> HorarioEspecialista.builder()
                        .especialistaId(id)
                        .sedeId(h.getSedeId() != null ? h.getSedeId().trim() : "")
                        .diaSemana(h.getDiaSemana())
                        .horaInicio(h.getHoraInicio())
                        .horaFin(h.getHoraFin())
                        .activo(true)
                        .build())
                .toList();

        esp.reemplazarHorarios(dominioHorarios);

        especialistaHorarioRepository.eliminarPorEspecialista(id);
        especialistaHorarioRepository.saveAll(dominioHorarios);

        auditoriaPort.registrar(
                "CONFIGURACION",
                "ACTUALIZAR_HORARIO_ESPECIALISTA",
                "especialistas",
                id.toString(),
                String.format("Horario semanal de %s actualizado: %d bloque(s)", esp.getNombre(), lista.size())
        );

        return obtenerHorarios(id);
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadResponseDTO obtenerDisponibilidad(UUID especialistaId, LocalDate fecha, String sedeId) {
        Especialista esp = especialistaRepository.findById(especialistaId)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado con ID: " + especialistaId));

        if (!esp.isActivo()) {
            return DisponibilidadResponseDTO.builder()
                    .especialistaId(especialistaId)
                    .especialistaNombre(esp.getNombre())
                    .fecha(fecha)
                    .sedeId(sedeId)
                    .atiendeEseDia(false)
                    .mensaje("El especialista se encuentra inactivo y no recibe nuevas citas.")
                    .slots(Collections.emptyList())
                    .build();
        }

        int diaSemana = fecha.getDayOfWeek().getValue(); // 1 = Lunes, 7 = Domingo

        List<HorarioEspecialista> horarios;
        if (sedeId != null && !sedeId.isBlank()) {
            horarios = especialistaHorarioRepository.findByEspecialistaIdAndSedeIdAndDiaSemanaAndActivoTrue(
                    especialistaId, sedeId.trim(), diaSemana);
        } else {
            horarios = especialistaHorarioRepository.findByEspecialistaIdAndDiaSemanaAndActivoTrue(
                    especialistaId, diaSemana);
        }

        if (horarios.isEmpty()) {
            return DisponibilidadResponseDTO.builder()
                    .especialistaId(especialistaId)
                    .especialistaNombre(esp.getNombre())
                    .fecha(fecha)
                    .sedeId(sedeId)
                    .atiendeEseDia(false)
                    .mensaje("El especialista no tiene turnos programados para el día seleccionado.")
                    .slots(Collections.emptyList())
                    .build();
        }

        // B-031: solo ocupan las citas que no liberan el hueco (CANCELADA, NO_ASISTIO y EXPIRADA no), igual que la colisión
        List<Cita> citas = citaRepository.findByDoctorIdAndFecha(especialistaId, fecha).stream()
                .filter(Cita::ocupaAgenda)
                .toList();

        // B-036: para hoy (hora de Lima) no se ofrecen franjas que ya empezaron. Para otras fechas no cambia nada, y
        // POST /citas sigue admitiendo citas en el pasado (registro retroactivo de atenciones).
        LocalDateTime ahora = LocalDateTime.now(reloj);
        boolean esHoy = fecha.equals(ahora.toLocalDate());

        Map<LocalTime, DisponibilidadSlotDTO> mapSlots = new TreeMap<>();
        for (HorarioEspecialista h : horarios) {
            LocalTime actual = h.getHoraInicio();
            while (actual.isBefore(h.getHoraFin())) {
                final LocalTime slotTime = actual;
                // B-030: la franja de 30 min está ocupada si se cruza con el intervalo de alguna cita (no solo si empieza dentro)
                boolean ocupado = citas.stream().anyMatch(c -> c.solapaCon(fecha, slotTime, 30));

                boolean pasado = esHoy && !slotTime.isAfter(ahora.toLocalTime());
                mapSlots.putIfAbsent(slotTime, DisponibilidadSlotDTO.builder()
                        .hora(slotTime)
                        .disponible(!ocupado && !pasado)
                        .motivoNoDisponible(ocupado ? "OCUPADO" : (pasado ? "PASADO" : null))
                        .build());

                actual = actual.plusMinutes(30);
            }
        }

        return DisponibilidadResponseDTO.builder()
                .especialistaId(especialistaId)
                .especialistaNombre(esp.getNombre())
                .fecha(fecha)
                .sedeId(sedeId)
                .atiendeEseDia(true)
                .mensaje("Turnos disponibles calculados con éxito.")
                .slots(new ArrayList<>(mapSlots.values()))
                .build();
    }

    @Transactional
    public EspecialistaResponseDTO asegurarPerfilDeOdontologo(UUID usuarioId, String nombre, String email, String sedeId) {
        return asegurarPerfilDeOdontologo(usuarioId, nombre, email, sedeId, null);
    }

    /** Crea o actualiza la ficha del doctor desde su cuenta: especialidad que puso el dueño, sede y sillón libre de esa sede. */
    @Transactional
    public EspecialistaResponseDTO asegurarPerfilDeOdontologo(UUID usuarioId, String nombre, String email, String sedeId, String especialidad) {
        String especialidadLimpia = especialidad == null || especialidad.isBlank() ? null : especialidad.trim();
        var vinculado = especialistaRepository.findByUsuarioId(usuarioId);
        if (vinculado.isPresent()) {
            Especialista esp = vinculado.get();
            boolean modificado = false;
            if (nombre != null && !nombre.isBlank() && !nombre.trim().equals(esp.getNombre())) {
                esp.setNombre(nombre.trim());
                modificado = true;
            }
            if (email != null && !email.isBlank() && !email.trim().equalsIgnoreCase(esp.getEmail())) {
                esp.setEmail(email.trim().toLowerCase());
                modificado = true;
            }
            if (especialidadLimpia != null && !especialidadLimpia.equals(esp.getEspecialidad())) {
                esp.setEspecialidad(especialidadLimpia);
                modificado = true;
            }
            if (sedeId != null && !especialistaRepository.findSedeIdsByEspecialistaId(esp.getId()).contains(sedeId)) {
                especialistaRepository.vincularSede(esp.getId(), sedeId);
                asignarPrimerSillonLibre(esp.getId(), sedeId);
            }
            if (modificado) {
                esp = especialistaRepository.save(esp);
            }
            return toDTO(esp);
        }

        var porCorreo = email != null
                ? especialistaRepository.findFirstByEmailIgnoreCaseAndUsuarioIdIsNull(email)
                : java.util.Optional.<Especialista>empty();

        if (porCorreo.isPresent()) {
            Especialista esp = porCorreo.get();
            esp.vincularUsuario(usuarioId);
            if (especialidadLimpia != null) {
                esp.setEspecialidad(especialidadLimpia);
            }
            Especialista guardado = especialistaRepository.save(esp);
            if (sedeId != null && !especialistaRepository.findSedeIdsByEspecialistaId(esp.getId()).contains(sedeId)) {
                especialistaRepository.vincularSede(esp.getId(), sedeId);
                asignarPrimerSillonLibre(esp.getId(), sedeId);
            }
            auditoriaPort.registrar(
                    "CONFIGURACION",
                    "VINCULAR_ESPECIALISTA_USUARIO",
                    "especialistas",
                    guardado.getId().toString(),
                    String.format("Especialista %s vinculado a la cuenta %s", guardado.getNombre(), email)
            );
            return toDTO(guardado);
        }

        EspecialistaResponseDTO creado = crear(EspecialistaRequestDTO.builder()
                .nombre(nombre)
                .especialidad(especialidadLimpia != null ? especialidadLimpia : Especialista.ESPECIALIDAD_POR_DEFECTO)
                .email(email)
                .sedes(List.of(sedeId))
                .usuarioId(usuarioId)
                .build(), false);

        reemplazarHorarios(creado.getId(), Especialista.DIAS_HABITUALES.stream()
                .map(dia -> HorarioEspecialistaRequestDTO.builder()
                        .sedeId(sedeId)
                        .diaSemana(dia)
                        .horaInicio(Especialista.INICIO_HABITUAL)
                        .horaFin(Especialista.FIN_HABITUAL)
                        .build())
                .toList());
        asignarPrimerSillonLibre(creado.getId(), sedeId);

        return creado;
    }

    /** Al dar de alta a un doctor en una sede se le asigna el primer sillón libre; si la sede está llena queda sin sillón y se asigna a mano. */
    private void asignarPrimerSillonLibre(UUID especialistaId, String sedeId) {
        if (sedeId == null) {
            return;
        }
        sedeValidacionPort.obtenerSede(sedeId).ifPresent(sede -> {
            for (int numero = 1; numero <= sede.sillones(); numero++) {
                if (especialistaRepository.findEspecialistaIdBySedeYSillon(sedeId, numero).isEmpty()) {
                    especialistaRepository.actualizarSillon(especialistaId, sedeId, numero);
                    return;
                }
            }
        });
    }

    /** El propio doctor cambia su especialidad desde su cuenta; se ve en la web y en el sistema. */
    @Override
    @Transactional
    public EspecialistaResponseDTO actualizarMiEspecialidad(UUID usuarioId, String especialidad) {
        String limpia = especialidad == null ? "" : especialidad.trim();
        if (limpia.isEmpty()) {
            throw new IllegalArgumentException("La especialidad es obligatoria.");
        }
        Especialista esp = especialistaRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Tu cuenta no tiene ficha de doctor."));
        esp.setEspecialidad(limpia);
        return toDTO(especialistaRepository.save(esp));
    }

    @Transactional
    public void sincronizarEstadoConUsuario(UUID usuarioId, boolean activo) {
        especialistaRepository.findByUsuarioId(usuarioId)
                .filter(esp -> esp.isActivo() != activo)
                .ifPresent(esp -> cambiarEstado(esp.getId(), activo));
    }

    private Set<String> validarSedes(List<String> sedes) {
        Set<String> limpias = new LinkedHashSet<>();
        if (sedes != null) {
            sedes.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(limpias::add);
        }
        if (limpias.isEmpty()) {
            throw new IllegalArgumentException("Debe asignar al menos una sede de atención.");
        }
        for (String sede : limpias) {
            if (!sedeValidacionPort.existeSede(sede)) {
                throw new IllegalArgumentException("La sede '" + sede + "' no existe.");
            }
        }
        return limpias;
    }

    private void verificarSedeAdmiteEspecialista(String sedeId) {
        sedeValidacionPort.obtenerSede(sedeId).ifPresent(SedeAgenda::verificarAdmiteEspecialista);
    }

    @Transactional
    public EspecialistaResponseDTO actualizarSillones(UUID especialistaId, SillonesEspecialistaRequestDTO request) {
        Especialista especialista = especialistaRepository.findById(especialistaId)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado."));
        Map<String, Integer> sillones = new java.util.HashMap<>(request.getSillonesPorSede());
        if (sillones.containsValue(null)) {
            throw new IllegalArgumentException("Cada sillón asignado debe tener un número válido.");
        }
        for (String sedeId : request.getSedesSinSillon()) {
            if (sillones.containsKey(sedeId)) {
                throw new IllegalArgumentException("Una sede no puede tener sillón asignado y sin asignar a la vez.");
            }
            sillones.put(sedeId, null);
        }
        if (!especialista.getSedes().containsAll(sillones.keySet())) {
            throw new IllegalArgumentException("Solo se pueden asignar sillones en sedes del especialista.");
        }

        for (Map.Entry<String, Integer> entrada : sillones.entrySet()) {
            String sedeId = entrada.getKey();
            SecurityUtils.validarAccesoSede(sedeId);
            Integer numero = entrada.getValue();
            SedeAgenda sede = sedeValidacionPort.obtenerSede(sedeId)
                    .orElseThrow(() -> new IllegalArgumentException("La sede '" + sedeId + "' no existe."));
            especialista.asignarSillonEnSede(sedeId, numero, sede);
            if (numero != null) {
                especialistaRepository.findEspecialistaIdBySedeYSillon(sedeId, numero)
                        .filter(otroId -> !otroId.equals(especialistaId))
                        .ifPresent(otroId -> {
                            throw new SillonEspecialistaFijoException(
                                    "El sillón #" + numero + " ya está asignado en la sede '" + sedeId + "'.");
                        });
            }
            especialistaRepository.actualizarSillon(especialistaId, sedeId, numero);
        }
        return toDTO(especialista);
    }

    @Transactional(readOnly = true)
    public MetaDoctorResponseDTO obtenerMeta(UUID especialistaId) {
        Especialista especialista = especialistaRepository.findById(especialistaId)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado."));
        return toMetaDTO(especialista);
    }

    @Transactional
    public MetaDoctorResponseDTO actualizarMeta(UUID especialistaId, MetaDoctorRequestDTO request) {
        Especialista especialista = especialistaRepository.findById(especialistaId)
                .orElseThrow(() -> new IllegalArgumentException("Especialista no encontrado."));
        especialista.definirMeta(request.getMetaMensual(), request.getComisionExcedentePorcentaje());
        especialistaRepository.save(especialista);
        return toMetaDTO(especialista);
    }

    private static MetaDoctorResponseDTO toMetaDTO(Especialista e) {
        return MetaDoctorResponseDTO.builder()
                .especialistaId(e.getId())
                .metaMensual(e.getMetaMensual())
                .comisionExcedentePorcentaje(e.getComisionExcedentePorcentaje())
                .build();
    }

    private void validarUsuarioVinculable(UUID usuarioId, UUID especialistaId) {
        if (usuarioId == null) {
            return;
        }
        var usuarioOpt = usuarioConsultaPort.findById(usuarioId);
        if (usuarioOpt.isEmpty()) {
            throw new IllegalArgumentException("El usuario a vincular no existe.");
        }
        var usuario = usuarioOpt.get();
        if ("RECEPCION".equalsIgnoreCase(usuario.rol())) {
            throw new IllegalArgumentException("No se puede vincular un usuario de Recepción como especialista.");
        }
        especialistaRepository.findByUsuarioId(usuarioId)
                .filter(otro -> !otro.getId().equals(especialistaId))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("Ese usuario ya está vinculado al especialista " + otro.getNombre() + ".");
                });
    }

    private static String normalizarOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
