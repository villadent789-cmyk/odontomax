package com.odontomax.tratamientos.service;

import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.infrastructure.persistence.entity.PlantillaPlanEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataPlantillaPlanRepository;
import com.odontomax.tratamientos.dto.PlantillaPlanDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Plantillas de plan de tratamiento del negocio activo. Guardan qué servicio, cuántas piezas, qué gravedad y cuántas
 * sesiones; el precio no se guarda: sale del catálogo vigente al usar la plantilla.
 */
@Service
@RequiredArgsConstructor
public class PlantillaPlanService {

    private final SpringDataPlantillaPlanRepository repositorio;

    @Transactional(readOnly = true)
    public List<PlantillaPlanDTO> listar() {
        return repositorio.findByUnidadDeNegocioAndActivoTrueOrderByNombreAsc(SecurityUtils.negocioDelCatalogo()).stream().map(this::aDto).toList();
    }

    @Transactional
    public PlantillaPlanDTO crear(PlantillaPlanDTO req) {
        ClinicaUserPrincipal user = SecurityUtils.getCurrentUser();
        String negocio = SecurityUtils.negocioDelCatalogo();
        String nombre = req.getNombre().trim();
        if (repositorio.existsByUnidadDeNegocioAndActivoTrueAndNombreIgnoreCase(negocio, nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una plantilla con el nombre «" + nombre + "».");
        }
        PlantillaPlanEntity guardada = repositorio.save(PlantillaPlanEntity.builder()
                .unidadDeNegocio(negocio)
                .nombre(nombre)
                .procedimientoId(req.getProcedimientoId().trim())
                .servicioNombre(req.getServicioNombre().trim())
                .piezas(req.getPiezas())
                .gravedad(req.getGravedad())
                .sesiones(req.getSesiones())
                .notas(req.getNotas() == null || req.getNotas().isBlank() ? null : req.getNotas().trim())
                .creadoPor(user != null ? user.nombre() : null)
                .build());
        return aDto(guardada);
    }

    /** Se desactiva en vez de borrar: los planes ya creados con ella no cambian. */
    @Transactional
    public void quitar(UUID id) {
        PlantillaPlanEntity plantilla = repositorio.findById(id)
                .filter(p -> p.getUnidadDeNegocio().equals(SecurityUtils.negocioDelCatalogo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantilla no encontrada"));
        plantilla.setActivo(false);
        repositorio.save(plantilla);
    }

    private PlantillaPlanDTO aDto(PlantillaPlanEntity p) {
        return PlantillaPlanDTO.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .procedimientoId(p.getProcedimientoId())
                .servicioNombre(p.getServicioNombre())
                .piezas(p.getPiezas())
                .gravedad(p.getGravedad())
                .sesiones(p.getSesiones())
                .notas(p.getNotas())
                .build();
    }
}
