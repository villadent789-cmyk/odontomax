package com.odontomax.inventario.service;

import com.odontomax.identidad.api.SecurityUtils;
import com.odontomax.inventario.api.ConsumoInsumosModuloApi;
import com.odontomax.inventario.dto.AlertaConsumoDTO;
import com.odontomax.inventario.dto.RecetaInsumoDTO;
import com.odontomax.sedes.api.ConsultaSedesModuloApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Receta de insumos de cada servicio de Villadent y descuento automático del stock al atender una cita. */
@Service
@RequiredArgsConstructor
public class ConsumoInsumosService implements ConsumoInsumosModuloApi {

    private final JdbcTemplate jdbc;
    private final ConsultaSedesModuloApi sedesApi;

    @Transactional(readOnly = true)
    public List<RecetaInsumoDTO> obtenerReceta(String servicioId) {
        return jdbc.query("""
                SELECT si.producto_id, p.nombre, p.unidad, si.cantidad
                  FROM servicio_insumos si JOIN productos_inventario p ON p.id = si.producto_id
                 WHERE si.servicio_id = ? ORDER BY p.nombre""",
                (rs, n) -> new RecetaInsumoDTO(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4)),
                servicioId);
    }

    @Transactional
    public List<RecetaInsumoDTO> guardarReceta(String servicioId, List<RecetaInsumoDTO> lineas) {
        if (sedesApi.obtenerIdServicioDeVilladent(servicioId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El servicio no existe o no es de Villadent.");
        }
        Set<UUID> vistos = new HashSet<>();
        for (RecetaInsumoDTO linea : lineas) {
            if (!vistos.add(linea.productoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un insumo no puede repetirse en la receta.");
            }
            Boolean activo = jdbc.query("SELECT activo FROM productos_inventario WHERE id = ?",
                    rs -> rs.next() ? rs.getBoolean(1) : null, linea.productoId());
            if (activo == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Insumo no encontrado: " + linea.productoId());
            }
            if (!activo) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un insumo de la receta fue dado de baja.");
            }
        }
        jdbc.update("DELETE FROM servicio_insumos WHERE servicio_id = ?", servicioId);
        for (RecetaInsumoDTO linea : lineas) {
            jdbc.update("INSERT INTO servicio_insumos (servicio_id, producto_id, cantidad) VALUES (?, ?, ?)",
                    servicioId, linea.productoId(), linea.cantidad());
        }
        return obtenerReceta(servicioId);
    }

    /** Propaga en una transacción propia: un fallo del inventario nunca debe deshacer la atención de la cita. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void descontarPorAtencion(UUID citaId, String sedeId, String servicioONombre) {
        Optional<String> servicioId = sedesApi.obtenerIdServicioDeVilladent(servicioONombre);
        if (citaId == null || sedeId == null || servicioId.isEmpty()) {
            return;
        }
        List<RecetaInsumoDTO> receta = obtenerReceta(servicioId.get());
        for (RecetaInsumoDTO insumo : receta) {
            // La clave única (cita, insumo) hace idempotente el descuento: si ya existe, no se toca nada.
            Integer stock = jdbc.query("SELECT cantidad FROM inventario_stock WHERE producto_id = ? AND sede_id = ? FOR UPDATE",
                    rs -> rs.next() ? rs.getInt(1) : 0, insumo.productoId(), sedeId);
            int descontar = Math.min(stock == null ? 0 : stock, insumo.cantidad());
            int creados = jdbc.update("""
                    INSERT INTO consumos_insumos (cita_id, producto_id, sede_id, cantidad_requerida, cantidad_descontada)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (cita_id, producto_id) DO NOTHING""",
                    citaId, insumo.productoId(), sedeId, insumo.cantidad(), descontar);
            if (creados == 1 && descontar > 0) {
                jdbc.update("""
                        INSERT INTO inventario_movimientos (producto_id, sede_id, tipo, cantidad, motivo, cita_id)
                        VALUES (?, ?, 'SALIDA', ?, ?, ?)""",
                        insumo.productoId(), sedeId, -descontar, "Consumo en atención: " + servicioONombre, citaId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AlertaConsumoDTO> alertasPendientes(String sedeId) {
        SecurityUtils.validarAccesoSede(sedeId);
        return jdbc.query("""
                SELECT c.id, c.cita_id, ci.fecha, ci.motivo_consulta, c.sede_id, c.producto_id, p.nombre,
                       c.cantidad_requerida, c.cantidad_descontada
                  FROM consumos_insumos c
                  JOIN citas ci ON ci.id = c.cita_id
                  JOIN productos_inventario p ON p.id = c.producto_id
                 WHERE c.sede_id = ? AND c.cantidad_descontada < c.cantidad_requerida AND NOT c.faltante_resuelto
                 ORDER BY ci.fecha DESC, p.nombre""",
                (rs, n) -> {
                    Date fecha = rs.getDate(3);
                    int requerida = rs.getInt(8);
                    int descontada = rs.getInt(9);
                    return new AlertaConsumoDTO(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            fecha != null ? fecha.toLocalDate() : null, rs.getString(4), rs.getString(5),
                            rs.getObject(6, UUID.class), rs.getString(7), requerida, descontada, requerida - descontada);
                }, sedeId);
    }

    @Transactional
    public void resolverAlerta(UUID alertaId) {
        String sedeId = jdbc.query("SELECT sede_id FROM consumos_insumos WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, alertaId);
        if (sedeId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta no encontrada.");
        }
        SecurityUtils.validarAccesoSede(sedeId);
        var usuario = SecurityUtils.getCurrentUser();
        jdbc.update("UPDATE consumos_insumos SET faltante_resuelto = true, resuelto_por = ?, resuelto_en = now() WHERE id = ?",
                usuario != null ? usuario.id() : null, alertaId);
    }
}
