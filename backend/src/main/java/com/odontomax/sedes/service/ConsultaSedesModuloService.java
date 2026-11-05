package com.odontomax.sedes.service;

import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import com.odontomax.sedes.api.SedeResumen;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataSedeRepository;
import com.odontomax.sedes.infrastructure.persistence.repository.SpringDataServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConsultaSedesModuloService implements ConsultaSedesModuloApi {

    private final SpringDataSedeRepository sedeRepository;
    private final SpringDataServicioRepository servicioRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existeSede(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return false;
        }
        return sedeRepository.existsById(sedeId.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConfiguracionSedeAgendaDTO> obtenerConfiguracionSede(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return Optional.empty();
        }
        return sedeRepository.findById(sedeId.trim())
                .map(s -> new ConfiguracionSedeAgendaDTO(
                        s.getId(),
                        s.getNombre(),
                        s.getUnidadDeNegocio() != null ? s.getUnidadDeNegocio().name() : "VILLADENT",
                        s.isActiva(),
                        s.getWhatsapp(),
                        s.getTelefono(),
                        s.getMontoAdelantoDefecto(),
                        s.getHorasExpiracionAdelanto() != null ? s.getHorasExpiracionAdelanto() : 2,
                        s.getHorasAnticipacionCancelacion() != null ? s.getHorasAnticipacionCancelacion() : 24,
                        s.getHorasRecordatorioWhatsapp() != null ? s.getHorasRecordatorioWhatsapp() : 24,
                        s.getHoraInicioLlamadas() != null ? s.getHoraInicioLlamadas() : java.time.LocalTime.of(8, 30),
                        s.getDiasVigenciaPromocionCumpleanos() != null ? s.getDiasVigenciaPromocionCumpleanos() : 7
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SedeResumen> obtenerSede(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return Optional.empty();
        }
        return sedeRepository.findById(sedeId.trim())
                .map(s -> new SedeResumen(s.getId(), s.isActiva(), s.getSillones() != null ? s.getSillones() : 0));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> obtenerNombreSede(String sedeId) {
        if (sedeId == null || sedeId.isBlank()) {
            return Optional.empty();
        }
        return sedeRepository.findById(sedeId.trim()).map(s -> s.getNombre());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listarIdsSedesActivas() {
        return sedeRepository.findByActivaTrueOrderByNombreAsc().stream().map(s -> s.getId()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EstudioDelCatalogo> obtenerEstudioDeVillamax(String servicioId) {
        if (servicioId == null || servicioId.isBlank()) {
            return Optional.empty();
        }
        String termino = servicioId.trim();
        return servicioRepository.findById(termino)
                .or(() -> servicioRepository.findByNombreIgnoreCase(termino))
                .filter(s -> s.isActivo() && "VILLAMAX".equals(s.getUnidadDeNegocio()))
                .map(s -> new EstudioDelCatalogo(s.getId(), s.getNombre(), s.getPrecioDesde(), s.getComisionPorcentaje(), s.getComisionFija()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> obtenerIdServicioDeVilladent(String idONombre) {
        if (idONombre == null || idONombre.isBlank()) {
            return Optional.empty();
        }
        String termino = idONombre.trim();
        return servicioRepository.findById(termino)
                .or(() -> servicioRepository.findByNombreIgnoreCase(termino))
                .filter(s -> s.isActivo() && "VILLADENT".equals(s.getUnidadDeNegocio()))
                .map(com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> obtenerUnidadDeNegocioDelServicio(String idONombre) {
        if (idONombre == null || idONombre.isBlank()) {
            return Optional.empty();
        }
        String termino = idONombre.trim();
        return servicioRepository.findById(termino)
                .map(com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity::getUnidadDeNegocio)
                .or(() -> servicioRepository.findByNombreIgnoreCase(termino)
                        .map(com.odontomax.sedes.infrastructure.persistence.entity.ServicioEntity::getUnidadDeNegocio));
    }
}
