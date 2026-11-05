package com.odontomax.sedes.api;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Consulta de sedes para otros módulos, sin exponer la persistencia de sedes. */
public interface ConsultaSedesModuloApi {

    /** true si existe una sede con ese id exacto (activa o inactiva); false si es null o en blanco. Recorta espacios. */
    boolean existeSede(String sedeId);

    record ConfiguracionSedeAgendaDTO(
            String id,
            String nombre,
            String unidadDeNegocio,
            boolean activa,
            String whatsapp,
            String telefono,
            BigDecimal montoAdelantoDefecto,
            int horasExpiracionAdelanto,
            int horasAnticipacionCancelacion,
            int horasRecordatorioWhatsapp,
            LocalTime horaInicioLlamadas,
            int diasVigenciaPromocionCumpleanos
    ) {}

    Optional<ConfiguracionSedeAgendaDTO> obtenerConfiguracionSede(String sedeId);

    /**
     * Estado y número de sillones de la sede con ese id exacto (activa o inactiva); vacío si no existe o si el id es
     * null o en blanco. Recorta espacios.
     */
    Optional<SedeResumen> obtenerSede(String sedeId);

    /** Nombre oficial de la sede con ese id exacto (activa o inactiva); vacio si no existe o si el id es null o en blanco. */
    Optional<String> obtenerNombreSede(String sedeId);

    /** Ids de las sedes activas, ordenados por nombre ascendente. */
    List<String> listarIdsSedesActivas();

    /** Estudio radiológico del catálogo de Villamax, con su tarifa fija y el porcentaje de comisión; vacío si no existe, está inactivo o no es de Villamax. */
    record EstudioDelCatalogo(String id, String nombre, BigDecimal precio, BigDecimal comisionPorcentaje, BigDecimal comisionFija) {}

    Optional<EstudioDelCatalogo> obtenerEstudioDeVillamax(String servicioId);

    /** Unidad de negocio (VILLADENT / VILLAMAX) del servicio por id o nombre exacto; vacío si no existe. */
    Optional<String> obtenerUnidadDeNegocioDelServicio(String idONombre);

    /** Id del servicio activo de Villadent con ese id o nombre exacto; vacío si no existe, está inactivo o es de otro negocio. */
    Optional<String> obtenerIdServicioDeVilladent(String idONombre);
}
