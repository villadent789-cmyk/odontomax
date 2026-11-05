package com.odontomax.pacientes.application.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.pacientes.api.ConsultaPacientesModuloApi;
import com.odontomax.pacientes.api.PacienteResumen;
import com.odontomax.pacientes.application.dto.request.PacienteRequestDTO;
import com.odontomax.pacientes.application.dto.response.PacienteResponseDTO;
import com.odontomax.pacientes.application.port.in.ActualizarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.ArchivarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.ConsultarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.EliminarPacienteUseCase;
import com.odontomax.pacientes.application.port.in.RegistrarPacienteUseCase;
import com.odontomax.pacientes.application.port.out.BorradoArchivosPort;
import com.odontomax.pacientes.application.port.out.ExpedienteDependenciasPort;
import com.odontomax.pacientes.application.port.out.HistorialCitasPort;
import com.odontomax.pacientes.application.port.out.PacienteNegocioPort;
import com.odontomax.pacientes.application.port.out.RegistroAuditoriaPort;
import com.odontomax.pacientes.infrastructure.persistence.entity.PacienteEntity;
import com.odontomax.pacientes.infrastructure.persistence.repository.SpringDataPacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PacienteApplicationService implements
        ConsultarPacienteUseCase,
        RegistrarPacienteUseCase,
        ActualizarPacienteUseCase,
        ArchivarPacienteUseCase,
        EliminarPacienteUseCase {

    private static final ZoneId ZONE_LIMA = ZoneId.of("America/Lima");

    private final SpringDataPacienteRepository pacienteRepository;
    private PacienteNegocioPort pacienteNegocio;

    /** Inyección por setter para no cambiar el constructor de este servicio. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPacienteNegocio(PacienteNegocioPort pacienteNegocio) {
        this.pacienteNegocio = pacienteNegocio;
    }
    private final ConsultaPacientesModuloApi consultaModulo;
    private final HistorialCitasPort historialCitas;
    private final ExpedienteDependenciasPort expedienteDependencias;
    private final BorradoArchivosPort borradoArchivos;
    private final RegistroAuditoriaPort registroAuditoria;
    private com.odontomax.pacientes.application.port.in.GestionarDescuentosRecomendacionUseCase descuentosRecomendacion;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDescuentosRecomendacion(com.odontomax.pacientes.application.port.in.GestionarDescuentosRecomendacionUseCase descuentosRecomendacion) {
        this.descuentosRecomendacion = descuentosRecomendacion;
    }

    @Override
    public List<PacienteResumen> listarCumpleanerosDeManana(String sedeId) {
        LocalDate manana = LocalDate.now(ZONE_LIMA).plusDays(1);
        return consultaModulo.findCumpleanerosDelDia(sedeId, manana.getMonthValue(), manana.getDayOfMonth());
    }

    @Override
    public List<PacienteResponseDTO> listar(String busqueda, String sedeId) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        boolean esOdontologo = usuario != null && "ODONTOLOGO".equalsIgnoreCase(usuario.rol());

        List<PacienteEntity> lista;
        if (esOdontologo) {
            String termino = (busqueda != null && !busqueda.isBlank()) ? busqueda.trim() : null;
            String sedeFiltro = (sedeEfectiva != null && !sedeEfectiva.isBlank() && !sedeEfectiva.equalsIgnoreCase("ALL")) ? sedeEfectiva.trim() : null;
            List<PacienteResponseDTO> odontoList = historialCitas.buscarPacientesOdontologo(termino, sedeFiltro, usuario.id());
            completarUltimaAtencion(odontoList);
            return odontoList;
        } else {
            boolean todasLasSedes = sedeEfectiva == null || sedeEfectiva.isBlank();
            // "Todas las sedes" del administrador general = todas las del negocio activo (Villadent o Villamax).
            String negocio = todasLasSedes ? SecurityUtils.negocioActivo() : null;
            if (negocio != null) {
                List<PacienteResponseDTO> delNegocio = (busqueda != null && !busqueda.isBlank())
                        ? pacienteNegocio.buscar(busqueda, negocio)
                        : pacienteNegocio.listarActivos(negocio);
                completarUltimaAtencion(delNegocio);
                return delNegocio;
            }
            if (busqueda != null && !busqueda.isBlank()) {
                lista = (!todasLasSedes)
                        ? pacienteRepository.searchByTermino(busqueda, sedeEfectiva)
                        : pacienteRepository.searchByTerminoAllSedes(busqueda);
            } else {
                lista = pacienteRepository.findAllActivos(sedeEfectiva);
            }
        }
        List<PacienteResponseDTO> dtos = lista.stream().map(PacienteResponseDTO::fromEntity).toList();
        completarUltimaAtencion(dtos);
        return dtos;
    }

    @Override
    public Page<PacienteResponseDTO> listarPaginado(String busqueda, String sedeId, Pageable pageable) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(sedeId);
        String sedeFiltro = (sedeEfectiva != null && !sedeEfectiva.isBlank() && !sedeEfectiva.equalsIgnoreCase("ALL")) ? sedeEfectiva.trim() : null;
        String terminoFiltro = (busqueda != null && !busqueda.isBlank()) ? busqueda.trim() : null;

        ClinicaUserPrincipal usuario = SecurityUtils.getCurrentUser();
        boolean esOdontologo = usuario != null && "ODONTOLOGO".equalsIgnoreCase(usuario.rol());

        Page<PacienteResponseDTO> pagina;
        if (esOdontologo) {
            Page<PacienteResponseDTO> odontoPage = historialCitas.buscarPacientesOdontologoPaginado(terminoFiltro, sedeFiltro, usuario.id(), pageable);
            completarUltimaAtencion(odontoPage.getContent());
            return odontoPage;
        } else {
            String negocio = sedeFiltro == null ? SecurityUtils.negocioActivo() : null;
            pagina = negocio != null
                    ? pacienteNegocio.buscarPaginado(terminoFiltro, negocio, pageable)
                    : pacienteRepository.searchPaginado(terminoFiltro, sedeFiltro, pageable)
                            .map(PacienteResponseDTO::fromEntity);
        }
        completarUltimaAtencion(pagina.getContent());
        return pagina;
    }

    @Override
    public PacienteResponseDTO obtenerPorId(UUID id) {
        historialCitas.exigirAccesoDeOdontologo(id);
        PacienteEntity paciente = obtener(id);
        validarSedeDelPaciente(paciente);
        return PacienteResponseDTO.fromEntity(paciente);
    }

    @Override
    @Transactional
    public PacienteResponseDTO registrar(PacienteRequestDTO request) {
        String sedeEfectiva = SecurityUtils.resolverSedeEfectiva(request.getSedeId());
        if (sedeEfectiva == null || sedeEfectiva.isBlank()) {
            throw new IllegalArgumentException("Debe especificar una sede válida para registrar al paciente.");
        }
        SecurityUtils.validarAccesoSede(sedeEfectiva);

        String textoAlergias = (request.getAlergias() != null && !request.getAlergias().isEmpty())
                ? String.join(",", request.getAlergias())
                : null;
        OffsetDateTime ahora = OffsetDateTime.now();
        PacienteEntity nuevo = PacienteEntity.builder()
                .id(UUID.randomUUID())
                .dni(request.getDni().trim())
                .nombres(request.getNombres().trim())
                .apellidos(request.getApellidos().trim())
                .fechaNacimiento(request.getFechaNacimiento())
                .telefono(request.getTelefono())
                .email(request.getEmail())
                .sedeId(sedeEfectiva)
                .genero(request.getGenero())
                .fuenteCaptacion(request.getFuenteCaptacion())
                .referidoPorPacienteId(request.getReferidoPorPacienteId())
                .referidoPorPacienteNombre(request.getReferidoPorPacienteNombre())
                .doctorReferidor(request.getDoctorReferidor())
                .consentimientoPromociones(request.isConsentimientoPromociones())
                .consentimientoPromocionesFecha(request.isConsentimientoPromociones() ? ahora : null)
                .numeroHistoriaClinica(request.getNumeroHistoriaClinica())
                .alergias(textoAlergias)
                .antecedentesMedicos(request.getAntecedentesMedicos())
                .gestante(request.isGestante())
                .semanasGestacion(request.getSemanasGestacion())
                .saldoPendiente(BigDecimal.ZERO)
                .archivado(false)
                .creadoEn(ahora)
                .build();
        PacienteResponseDTO respuesta = PacienteResponseDTO.fromEntity(pacienteRepository.save(nuevo));
        if (request.getReferidoPorPacienteId() != null && descuentosRecomendacion != null) {
            // El id lo asigna la base al guardar: se toma de la respuesta.
            descuentosRecomendacion.registrarRecomendacion(request.getReferidoPorPacienteId(), respuesta.getId());
        }
        return respuesta;
    }

    @Override
    @Transactional
    public PacienteResponseDTO actualizar(UUID id, PacienteRequestDTO request) {
        historialCitas.exigirAccesoDeOdontologo(id);
        PacienteEntity existente = obtener(id);
        validarSedeDelPaciente(existente);

        String sedeEfectiva = (request.getSedeId() != null && !request.getSedeId().isBlank())
                ? SecurityUtils.resolverSedeEfectiva(request.getSedeId())
                : existente.getSedeId();
        if (sedeEfectiva != null) {
            SecurityUtils.validarAccesoSede(sedeEfectiva);
        }

        OffsetDateTime ahora = OffsetDateTime.now();
        existente.setDni(request.getDni().trim());
        existente.setNombres(request.getNombres().trim());
        existente.setApellidos(request.getApellidos().trim());
        existente.setFechaNacimiento(request.getFechaNacimiento());
        existente.setTelefono(request.getTelefono());
        existente.setEmail(request.getEmail());
        existente.setSedeId(sedeEfectiva);
        existente.setGenero(request.getGenero());
        existente.setFuenteCaptacion(request.getFuenteCaptacion());
        existente.setReferidoPorPacienteId(request.getReferidoPorPacienteId());
        existente.setReferidoPorPacienteNombre(request.getReferidoPorPacienteNombre());
        existente.setDoctorReferidor(request.getDoctorReferidor());
        if (request.isConsentimientoPromociones() && !Boolean.TRUE.equals(existente.getConsentimientoPromociones())) {
            existente.setConsentimientoPromocionesFecha(ahora);
        }
        existente.setConsentimientoPromociones(request.isConsentimientoPromociones());
        if (request.getNumeroHistoriaClinica() != null && !request.getNumeroHistoriaClinica().isBlank()) {
            existente.setNumeroHistoriaClinica(request.getNumeroHistoriaClinica());
        }
        if (request.getAlergias() != null && !request.getAlergias().isEmpty()) {
            existente.setAlergias(String.join(",", request.getAlergias()));
        } else {
            existente.setAlergias(null);
        }
        existente.setAntecedentesMedicos(request.getAntecedentesMedicos());
        existente.setGestante(request.isGestante());
        existente.setSemanasGestacion(request.getSemanasGestacion());

        return PacienteResponseDTO.fromEntity(pacienteRepository.save(existente));
    }

    @Override
    @Transactional
    public void archivar(UUID id, String motivo) {
        PacienteEntity existente = obtener(id);
        validarSedeDelPaciente(existente);
        existente.verificarArchivable(motivo);
        existente.setArchivado(true);
        existente.setMotivoArchivo(motivo);
        pacienteRepository.save(existente);
    }

    @Override
    @Transactional
    public void eliminar(UUID id, ClinicaUserPrincipal principal, String clientIp, String motivo) {
        PacienteEntity paciente = obtener(id);
        validarSedeDelPaciente(paciente);

        paciente.verificarEliminable(expedienteDependencias.contarRegistrosProtegidos(id));

        List<String> archivosABorrar = expedienteDependencias.archivosRadiograficos(id);
        expedienteDependencias.eliminarDependientes(id);

        pacienteRepository.deleteById(id);
        log.info("Paciente con ID {} eliminado exitosamente de la base de datos", id);

        registroAuditoria.registrarEliminacion(
                principal,
                id,
                String.format("Paciente eliminado permanentemente de Supabase y Cloudflare R2: %s %s (DNI: %s, Sede: %s) | Motivo: %s",
                        paciente.getNombres(), paciente.getApellidos(), paciente.getDni(), paciente.getSedeId(),
                        PacienteEntity.motivoParaAuditoria(motivo)),
                clientIp);

        borradoArchivos.borrarTrasCommit(archivosABorrar);
    }

    private PacienteEntity obtener(UUID id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));
    }

    private static void validarSedeDelPaciente(PacienteEntity paciente) {
        if (paciente.getSedeId() != null) {
            SecurityUtils.validarAccesoSede(paciente.getSedeId());
        }
    }

    private void completarUltimaAtencion(List<PacienteResponseDTO> dtos) {
        Map<UUID, LocalDate> ultimas = historialCitas.ultimasAtenciones(
                dtos.stream().map(PacienteResponseDTO::getId).toList());
        if (ultimas == null) return;
        dtos.forEach(d -> d.setUltimaCitaAtendida(ultimas.get(d.getId())));
    }
}
