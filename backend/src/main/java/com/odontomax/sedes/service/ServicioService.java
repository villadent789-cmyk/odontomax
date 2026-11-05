package com.odontomax.sedes.service;

import com.odontomax.sedes.dto.ServicioRequestDTO;
import com.odontomax.sedes.dto.ServicioResponseDTO;
import com.odontomax.auditoria.api.RegistrarAuditoriaApi;
import com.odontomax.auditoria.api.RegistroAuditoria;
import com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataServicioRepository;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioService {

    private final SpringDataServicioRepository servicioRepository;
    private final RegistrarAuditoriaApi registrarAuditoriaApi;

    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarServicios() {
        return listarServicios(SecurityUtils.negocioDelCatalogo());
    }

    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarServicios(String negocio) {
        String neg = (negocio != null && !negocio.isBlank()) ? negocio.trim().toUpperCase() : SecurityUtils.negocioDelCatalogo();
        return servicioRepository.findByActivoTrueAndUnidadDeNegocioOrderByOrdenAsc(neg).stream().map(this::toDTO).toList();
    }

    /** Catálogo de la reserva web: solo los servicios marcados como visibles. */
    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarServiciosPublicos(String negocio) {
        String neg = (negocio != null && !negocio.isBlank()) ? negocio.trim().toUpperCase() : SecurityUtils.negocioDelCatalogo();
        return servicioRepository.findByActivoTrueAndVisibleEnReservaTrueAndUnidadDeNegocioOrderByOrdenAsc(neg).stream().map(this::toDTO).toList();
    }

    /** Listado de administración: incluye inactivos para poder reactivarlos. */
    @Transactional(readOnly = true)
    public List<ServicioResponseDTO> listarTodos() {
        return servicioRepository.findByUnidadDeNegocioOrderByOrdenAscNombreAsc(SecurityUtils.negocioDelCatalogo()).stream().map(this::toDTO).toList();
    }

    @Transactional
    public ServicioResponseDTO crear(ServicioRequestDTO dto) {
        ServicioEntity srv = ServicioEntity.builder()
                .id(generarIdUnico(dto.getNombre()))
                .activo(true)
                .unidadDeNegocio(SecurityUtils.negocioDelCatalogo())
                .build();
        aplicar(srv, dto);
        ServicioEntity guardado = servicioRepository.save(srv);
        registrarAuditoria("CREAR_SERVICIO", guardado.getId(),
                String.format("Servicio creado: %s | %d min | S/ %s", guardado.getNombre(), guardado.getDuracionMin(), guardado.getPrecioDesde()));
        return toDTO(guardado);
    }

    @Transactional
    public ServicioResponseDTO actualizar(String id, ServicioRequestDTO dto) {
        ServicioEntity srv = servicioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado: " + id));
        aplicarCambios(srv, dto);
        ServicioEntity guardado = servicioRepository.save(srv);
        registrarAuditoria("EDITAR_SERVICIO", id,
                String.format("Servicio actualizado: %s | %d min | S/ %s", guardado.getNombre(), guardado.getDuracionMin(), guardado.getPrecioDesde()));
        return toDTO(guardado);
    }

    @Transactional
    public ServicioResponseDTO cambiarEstado(String id, boolean activo) {
        ServicioEntity srv = servicioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado: " + id));
        srv.setActivo(activo);
        ServicioEntity guardado = servicioRepository.save(srv);
        registrarAuditoria(activo ? "ACTIVAR_SERVICIO" : "DESACTIVAR_SERVICIO", id,
                String.format("Servicio %s marcado como %s", guardado.getNombre(), activo ? "ACTIVO" : "INACTIVO"));
        return toDTO(guardado);
    }

    private void aplicar(ServicioEntity srv, ServicioRequestDTO dto) {
        srv.setNombre(dto.getNombre().trim());
        srv.setEspecialidad(dto.getEspecialidad().trim());
        srv.setDescripcion(dto.getDescripcion() == null || dto.getDescripcion().isBlank() ? null : dto.getDescripcion().trim());
        srv.setDuracionMin(dto.getDuracionMin());
        srv.setPrecioDesde(escalaDeColumna(dto.getPrecio()));
        if (dto.getOrden() != null) {
            srv.setOrden(dto.getOrden());
        }
        srv.setComisionPorcentaje(dto.getComisionPorcentaje());
        srv.setComisionFija(dto.getComisionFija());
        srv.setPrecioLeve(dto.getPrecioLeve());
        srv.setPrecioModerado(dto.getPrecioModerado());
        srv.setPrecioSevero(dto.getPrecioSevero());
    }

    /**
     * N-003: en el PUT, {@code precio}, {@code descripcion} y {@code orden} omitidos o nulos conservan el valor actual
     * (un cliente que envía solo lo que cambia no pierde datos). Una descripción en blanco la borra; el precio no se
     * puede quitar por PUT (puede ponerse a 0).
     */
    private void aplicarCambios(ServicioEntity srv, ServicioRequestDTO dto) {
        srv.setNombre(dto.getNombre().trim());
        srv.setEspecialidad(dto.getEspecialidad().trim());
        if (dto.getDescripcion() != null) {
            srv.setDescripcion(dto.getDescripcion().isBlank() ? null : dto.getDescripcion().trim());
        }
        srv.setDuracionMin(dto.getDuracionMin());
        if (dto.getPrecio() != null) {
            srv.setPrecioDesde(escalaDeColumna(dto.getPrecio()));
        }
        if (dto.getComisionPorcentaje() != null) {
            srv.setComisionPorcentaje(dto.getComisionPorcentaje());
        }
        if (dto.getComisionFija() != null) {
            srv.setComisionFija(dto.getComisionFija());
        }
        if (dto.getPrecioLeve() != null) {
            srv.setPrecioLeve(dto.getPrecioLeve());
        }
        if (dto.getPrecioModerado() != null) {
            srv.setPrecioModerado(dto.getPrecioModerado());
        }
        if (dto.getPrecioSevero() != null) {
            srv.setPrecioSevero(dto.getPrecioSevero());
        }
        if (dto.getOrden() != null) {
            srv.setOrden(dto.getOrden());
        }
    }

    /**
     * B-025: el precio se guarda, se responde y se audita con la escala de {@code precio_desde numeric(10,2)}.
     * La validación HTTP ya rechaza más de 2 decimales; el redondeo HALF_UP (el mismo que aplica PostgreSQL) solo
     * protege a las llamadas directas al servicio.
     */
    private static BigDecimal escalaDeColumna(BigDecimal precio) {
        return precio == null ? null : precio.setScale(2, RoundingMode.HALF_UP);
    }

    /** Id legible derivado del nombre (p. ej. "limpieza-dental"), con sufijo si ya existe. */
    private String generarIdUnico(String nombre) {
        String base = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = "servicio";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40).replaceAll("-+$", "");
        }
        String candidato = base;
        int sufijo = 2;
        while (servicioRepository.existsById(candidato)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    private ServicioResponseDTO toDTO(ServicioEntity s) {
        return ServicioResponseDTO.builder()
                .id(s.getId())
                .nombre(s.getNombre())
                .especialidad(s.getEspecialidad())
                .descripcion(s.getDescripcion())
                .duracionMin(s.getDuracionMin())
                .precio(s.getPrecioDesde())
                .activo(s.isActivo())
                .orden(s.getOrden())
                .comisionPorcentaje(s.getComisionPorcentaje())
                .comisionFija(s.getComisionFija())
                .precioLeve(s.getPrecioLeve())
                .precioModerado(s.getPrecioModerado())
                .precioSevero(s.getPrecioSevero())
                .build();
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
                "servicios_web",
                registroId,
                detalle,
                SecurityUtils.getClientIp()));
    }
}
