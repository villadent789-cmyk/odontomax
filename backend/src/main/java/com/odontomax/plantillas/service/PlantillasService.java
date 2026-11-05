package com.odontomax.plantillas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odontomax.agenda.api.ConsultaEspecialistasModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.plantillas.dto.PlantillaTratamientoDTO;
import com.odontomax.plantillas.dto.RegistroPlantillaDTO;
import com.odontomax.plantillas.dto.RegistroPlantillaRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Plantillas clínicas por tratamiento y las fichas que los doctores llenan con ellas. */
@Service
@RequiredArgsConstructor
public class PlantillasService {

    private static final String SELECT_REGISTRO = """
            SELECT r.id, r.paciente_id, r.tratamiento_id, r.cita_id, r.plantilla_codigo, p.nombre AS plantilla_nombre,
                   r.plantilla_version, r.datos::text AS datos, r.estado, r.corrige_id, r.creado_por_nombre, r.creado_en, r.cerrado_en
              FROM registros_plantilla r JOIN plantillas_tratamiento p ON p.codigo = r.plantilla_codigo""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ConsultaEspecialistasModuloApi especialistasApi;

    private JsonNode leer(String texto) {
        try {
            return json.readTree(texto == null ? "{}" : texto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON inválido en la base de datos", e);
        }
    }

    private String texto(JsonNode nodo) {
        try {
            return json.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de la ficha no son válidos.");
        }
    }

    private RowMapper<PlantillaTratamientoDTO> mapaPlantilla() {
        return (rs, n) -> new PlantillaTratamientoDTO(
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("negocio"), rs.getString("descripcion"),
                rs.getInt("version"), leer(rs.getString("esquema")));
    }

    private RowMapper<RegistroPlantillaDTO> mapaRegistro() {
        return (rs, n) -> new RegistroPlantillaDTO(
                rs.getObject("id", UUID.class), rs.getObject("paciente_id", UUID.class), rs.getObject("tratamiento_id", UUID.class),
                rs.getObject("cita_id", UUID.class), rs.getString("plantilla_codigo"), rs.getString("plantilla_nombre"),
                rs.getInt("plantilla_version"), leer(rs.getString("datos")), rs.getString("estado"),
                rs.getObject("corrige_id", UUID.class), rs.getString("creado_por_nombre"),
                rs.getObject("creado_en", OffsetDateTime.class), rs.getObject("cerrado_en", OffsetDateTime.class));
    }

    @Transactional(readOnly = true)
    public List<PlantillaTratamientoDTO> listar(String negocio) {
        String n = negocio != null && !negocio.isBlank() ? negocio.trim().toUpperCase() : null;
        return jdbc.query("SELECT codigo, nombre, negocio, descripcion, version, esquema::text AS esquema FROM plantillas_tratamiento "
                + "WHERE activo AND (?::text IS NULL OR negocio = ?) ORDER BY negocio, nombre", mapaPlantilla(), n, n);
    }

    /** Plantilla del servicio (por id o nombre exacto); 404 si ese servicio no tiene plantilla. */
    @Transactional(readOnly = true)
    public PlantillaTratamientoDTO deServicio(String idONombre) {
        List<PlantillaTratamientoDTO> r = jdbc.query("""
                SELECT p.codigo, p.nombre, p.negocio, p.descripcion, p.version, p.esquema::text AS esquema
                  FROM servicio_plantilla sp
                  JOIN servicios_web s ON s.id = sp.servicio_id
                  JOIN plantillas_tratamiento p ON p.codigo = sp.plantilla_codigo
                 WHERE p.activo AND (s.id = ? OR lower(s.nombre) = lower(?))""", mapaPlantilla(), idONombre, idONombre);
        if (r.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese servicio no tiene plantilla clínica.");
        }
        return r.get(0);
    }

    private String sedeDelPaciente(UUID pacienteId) {
        String sede = jdbc.query("SELECT sede_id FROM pacientes WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, pacienteId);
        if (sede == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado.");
        }
        return sede;
    }

    private void exigirAcceso(UUID pacienteId) {
        especialistasApi.exigirAccesoDeOdontologo(pacienteId);
        SecurityUtils.validarAccesoSede(sedeDelPaciente(pacienteId));
    }

    @Transactional(readOnly = true)
    public List<RegistroPlantillaDTO> registrosDelPaciente(UUID pacienteId) {
        exigirAcceso(pacienteId);
        return jdbc.query(SELECT_REGISTRO + " WHERE r.paciente_id = ? ORDER BY r.creado_en DESC", mapaRegistro(), pacienteId);
    }

    @Transactional
    public RegistroPlantillaDTO crear(RegistroPlantillaRequestDTO req) {
        exigirAcceso(req.pacienteId());
        Integer version = jdbc.query("SELECT version FROM plantillas_tratamiento WHERE codigo = ? AND activo",
                rs -> rs.next() ? rs.getInt(1) : null, req.plantillaCodigo());
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantilla no encontrada.");
        }
        if (req.corrigeId() != null) {
            Boolean cerradoDelPaciente = jdbc.query("SELECT estado = 'CERRADO' AND paciente_id = ? FROM registros_plantilla WHERE id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : null, req.pacienteId(), req.corrigeId());
            if (!Boolean.TRUE.equals(cerradoDelPaciente)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se corrige una ficha cerrada del mismo paciente.");
            }
        }
        ClinicaUserPrincipal u = SecurityUtils.getCurrentUser();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO registros_plantilla (id, paciente_id, tratamiento_id, cita_id, plantilla_codigo, plantilla_version, datos, corrige_id, creado_por, creado_por_nombre)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)""",
                id, req.pacienteId(), req.tratamientoId(), req.citaId(), req.plantillaCodigo(), version, texto(req.datos()),
                req.corrigeId(), u != null ? u.id() : null, u != null ? u.nombre() : null);
        return obtener(id);
    }

    private RegistroPlantillaDTO obtener(UUID id) {
        List<RegistroPlantillaDTO> r = jdbc.query(SELECT_REGISTRO + " WHERE r.id = ?", mapaRegistro(), id);
        if (r.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha no encontrada.");
        }
        return r.get(0);
    }

    @Transactional
    public RegistroPlantillaDTO guardarDatos(UUID id, JsonNode datos) {
        RegistroPlantillaDTO actual = obtener(id);
        exigirAcceso(actual.pacienteId());
        if (!"BORRADOR".equals(actual.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La ficha ya está cerrada: no se puede editar. Crea una corrección.");
        }
        jdbc.update("UPDATE registros_plantilla SET datos = ?::jsonb WHERE id = ? AND estado = 'BORRADOR'", texto(datos), id);
        return obtener(id);
    }

    @Transactional
    public RegistroPlantillaDTO cerrar(UUID id) {
        RegistroPlantillaDTO actual = obtener(id);
        exigirAcceso(actual.pacienteId());
        if (!"BORRADOR".equals(actual.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La ficha ya estaba cerrada.");
        }
        JsonNode esquema = leer(jdbc.queryForObject("SELECT esquema::text FROM plantillas_tratamiento WHERE codigo = ?", String.class, actual.plantillaCodigo()));
        for (JsonNode seccion : esquema.path("secciones")) {
            for (JsonNode campo : seccion.path("campos")) {
                if (campo.path("obligatorio").asBoolean(false)) {
                    JsonNode valor = actual.datos().path(campo.path("id").asText());
                    if (valor.isMissingNode() || valor.isNull() || valor.asText("").isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta completar: " + campo.path("etiqueta").asText());
                    }
                }
            }
        }
        jdbc.update("UPDATE registros_plantilla SET estado = 'CERRADO', cerrado_en = now() WHERE id = ? AND estado = 'BORRADOR'", id);
        return obtener(id);
    }
}
