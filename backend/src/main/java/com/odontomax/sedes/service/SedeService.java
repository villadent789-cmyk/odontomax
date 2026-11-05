package com.odontomax.sedes.service;

import com.odontomax.sedes.dto.SedeRequestDTO;
import com.odontomax.sedes.dto.SedeResponseDTO;
import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.sedes.infrastructure.persistence.entity.SedeEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SedeService {

    // Valores por defecto de la tabla sedes (V1): un INSERT con null explícito no los aplicaría
    private static final String CIUDAD_POR_DEFECTO = "Huancayo";
    private static final String COLOR_POR_DEFECTO = "#C59B27";
    private static final String COLOR_SECUNDARIO_POR_DEFECTO = "#FEF3C7";

    private final SpringDataSedeRepository sedeRepository;
    private final RegistrarAuditoriaApi registrarAuditoriaApi;

    /** Listado público (web y selectores): solo sedes activas, excluyendo sedes inexistentes/obsoletas. */
    @Transactional(readOnly = true)
    public List<SedeResponseDTO> listarActivas() {
        return listarActivas(null);
    }

    /** Listado público filtrado por unidad de negocio (VILLADENT o VILLAMAX). Si negocio es null, lista todas. */
    @Transactional(readOnly = true)
    public List<SedeResponseDTO> listarActivas(String negocio) {
        var stream = sedeRepository.findByActivaTrueOrderByNombreAsc().stream()
                .filter(s -> !"sede-chilca-2".equalsIgnoreCase(s.getId()));
        if (negocio != null && !negocio.isBlank()) {
            try {
                com.odontomax.sedes.model.UnidadDeNegocio u = com.odontomax.sedes.model.UnidadDeNegocio.valueOf(negocio.trim().toUpperCase());
                stream = stream.filter(s -> s.getUnidadDeNegocio() == u);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return stream.map(this::toDTO).toList();
    }

    /** Listado de administración: incluye inactivas para poder reactivarlas, excluyendo sedes inexistentes. */
    @Transactional(readOnly = true)
    public List<SedeResponseDTO> listarTodas() {
        return sedeRepository.findAllByOrderByNombreAsc().stream()
                .filter(s -> !"sede-chilca-2".equalsIgnoreCase(s.getId()))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public SedeResponseDTO crear(SedeRequestDTO dto) {
        SedeEntity sede = SedeEntity.builder()
                .id(generarIdUnico(dto.getNombre()))
                .ciudad(CIUDAD_POR_DEFECTO)
                .color(COLOR_POR_DEFECTO)
                .colorSecundario(COLOR_SECUNDARIO_POR_DEFECTO)
                .activa(true)
                .build();
        aplicar(sede, dto);
        SedeEntity guardada = sedeRepository.save(sede);
        registrarAuditoria("CREAR_SEDE", guardada.getId(),
                String.format("Sede creada: %s | %s | %d sillón(es)", guardada.getNombre(), guardada.getDireccion(), guardada.getSillones()));
        return toDTO(guardada);
    }

    @Transactional
    public SedeResponseDTO actualizar(String id, SedeRequestDTO dto) {
        SedeEntity sede = buscar(id);
        aplicar(sede, dto);
        SedeEntity guardada = sedeRepository.save(sede);
        registrarAuditoria("EDITAR_SEDE", id,
                String.format("Sede actualizada: %s | %s | %d sillón(es)", guardada.getNombre(), guardada.getDireccion(), guardada.getSillones()));
        return toDTO(guardada);
    }

    @Transactional
    public SedeResponseDTO cambiarEstado(String id, boolean activa) {
        SedeEntity sede = buscar(id);
        sede.setActiva(activa);
        SedeEntity guardada = sedeRepository.save(sede);
        registrarAuditoria(activa ? "ACTIVAR_SEDE" : "DESACTIVAR_SEDE", id,
                String.format("Sede %s marcada como %s", guardada.getNombre(), activa ? "ACTIVA" : "INACTIVA"));
        return toDTO(guardada);
    }

    @Transactional
    public void eliminar(String id) {
        if ("sede-chilca-1".equalsIgnoreCase(id) || "sede-eltambo".equalsIgnoreCase(id) || "sede-villamax-chilca".equalsIgnoreCase(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden eliminar las sedes oficiales principales.");
        }
        SedeEntity sede = buscar(id);
        sedeRepository.delete(sede);
        registrarAuditoria("ELIMINAR_SEDE", id, "Sede eliminada: " + sede.getNombre());
    }

    private SedeEntity buscar(String id) {
        if ("sede-chilca-2".equalsIgnoreCase(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada: " + id);
        }
        return sedeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede no encontrada: " + id));
    }

    /** Campos editables del contrato; id, fotos, coordenadas y estado no se tocan aquí. */
    private void aplicar(SedeEntity sede, SedeRequestDTO dto) {
        sede.setNombre(dto.getNombre().trim());
        sede.setDireccion(dto.getDireccion().trim());
        sede.setSillones(dto.getSillones());
        sede.setTelefono(normalizarOpcional(dto.getTelefono()));
        sede.setWhatsapp(normalizarOpcional(dto.getWhatsapp()));
        sede.setEmail(normalizarOpcional(dto.getEmail()));
        sede.setHorarioTexto(normalizarOpcional(dto.getHorarioTexto()));
        sede.setDescripcion(normalizarOpcional(dto.getDescripcion()));
        // Columnas NOT NULL o con valor por defecto: en blanco se conserva el valor actual
        if (normalizarOpcional(dto.getCiudad()) != null) {
            sede.setCiudad(dto.getCiudad().trim());
        }
        if (normalizarOpcional(dto.getColor()) != null) {
            sede.setColor(dto.getColor().trim());
        }
        if (normalizarOpcional(dto.getColorSecundario()) != null) {
            sede.setColorSecundario(dto.getColorSecundario().trim());
        }
        if (dto.getUnidadDeNegocio() != null) {
            sede.setUnidadDeNegocio(dto.getUnidadDeNegocio());
        }
        if (dto.getRuc() != null) {
            sede.setRuc(normalizarOpcional(dto.getRuc()));
        }
    }

    /** Id "sede-" + slug del nombre (p. ej. "sede-huancayo-centro"), con sufijo -2, -3... si ya existe. */
    private String generarIdUnico(String nombre) {
        String slug = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            slug = "nueva";
        }
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        String base = "sede-" + slug;
        String candidato = base;
        int sufijo = 2;
        while (sedeRepository.existsById(candidato)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    private SedeResponseDTO toDTO(SedeEntity entity) {
        if (entity == null) return null;
        return SedeResponseDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .direccion(entity.getDireccion())
                .ciudad(entity.getCiudad())
                .telefono(entity.getTelefono())
                .whatsapp(entity.getWhatsapp())
                .email(entity.getEmail())
                .horarioTexto(entity.getHorarioTexto())
                .color(entity.getColor())
                .colorSecundario(entity.getColorSecundario())
                .sillones(entity.getSillones())
                .descripcion(entity.getDescripcion())
                .fotoPortada(entity.getFotoPortada())
                .fotos(entity.getFotos() != null ? Arrays.asList(entity.getFotos()) : List.of())
                .latitud(entity.getLatitud())
                .longitud(entity.getLongitud())
                .activa(entity.isActiva())
                .unidadDeNegocio(entity.getUnidadDeNegocio())
                .ruc(entity.getRuc())
                .montoAdelantoDefecto(entity.getMontoAdelantoDefecto())
                .horasExpiracionAdelanto(entity.getHorasExpiracionAdelanto())
                .horasAnticipacionCancelacion(entity.getHorasAnticipacionCancelacion())
                .horasRecordatorioWhatsapp(entity.getHorasRecordatorioWhatsapp())
                .horaInicioLlamadas(entity.getHoraInicioLlamadas())
                .diasVigenciaPromocionCumpleanos(entity.getDiasVigenciaPromocionCumpleanos())
                .build();
    }

    private static String normalizarOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private void registrarAuditoria(String accion, String registroId, String detalle) {
        ClinicaUserPrincipal principal = SecurityUtils.getCurrentUser();
        registrarAuditoriaApi.registrar(new RegistroAuditoria(
                principal != null ? principal.id() : null,
                principal != null ? principal.nombre() : "SISTEMA",
                principal != null ? principal.rol() : "SISTEMA",
                principal != null ? principal.sedeId() : null,
                "CONFIGURACION",
                accion,
                "sedes",
                registroId,
                detalle,
                SecurityUtils.getClientIp()));
    }
}
