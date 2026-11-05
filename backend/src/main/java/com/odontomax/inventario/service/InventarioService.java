package com.odontomax.inventario.service;

import com.odontomax.application.dto.request.InventarioMovimientoRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioCreateRequestDTO;
import com.odontomax.application.dto.request.ProductoInventarioUpdateRequestDTO;
import com.odontomax.application.dto.request.TransferenciaCreateRequestDTO;
import com.odontomax.application.dto.response.InventarioMovimientoResponseDTO;
import com.odontomax.application.dto.response.ProductoInventarioResponseDTO;
import com.odontomax.application.dto.response.TransferenciaResponseDTO;
import com.odontomax.infrastructure.persistence.entity.AuditoriaLogEntity;
import com.odontomax.infrastructure.persistence.entity.InventarioMovimientoEntity;
import com.odontomax.infrastructure.persistence.entity.InventarioStockEntity;
import com.odontomax.infrastructure.persistence.entity.ProductoInventarioEntity;
import com.odontomax.infrastructure.persistence.entity.TransferenciaInventarioEntity;
import com.odontomax.infrastructure.persistence.repository.SpringDataAuditoriaRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataInventarioMovimientoRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataInventarioStockRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataProductoInventarioRepository;
import com.odontomax.infrastructure.persistence.repository.SpringDataTransferenciaInventarioRepository;
import com.odontomax.identidad.api.ConsultaUsuarioModuloApi;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventarioService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final SpringDataProductoInventarioRepository productoRepository;
    private final SpringDataInventarioStockRepository stockRepository;
    private final SpringDataInventarioMovimientoRepository movimientoRepository;
    private final SpringDataTransferenciaInventarioRepository transferenciaRepository;
    private final ConsultaUsuarioModuloApi consultaUsuarioModuloApi;
    private final SpringDataAuditoriaRepository auditoriaRepository;

    /** Une dos filas [productoId, próximo vencimiento, última entrega]: el vencimiento más cercano y la entrega más reciente. */
    private static Object[] combinarFechas(Object[] a, Object[] b) {
        LocalDate vencimiento = (LocalDate) a[1];
        if (b[1] != null && (vencimiento == null || ((LocalDate) b[1]).isBefore(vencimiento))) {
            vencimiento = (LocalDate) b[1];
        }
        LocalDate entrega = (LocalDate) a[2];
        if (b[2] != null && (entrega == null || ((LocalDate) b[2]).isAfter(entrega))) {
            entrega = (LocalDate) b[2];
        }
        return new Object[]{a[0], vencimiento, entrega};
    }

    @Transactional(readOnly = true)
    public List<ProductoInventarioResponseDTO> listarProductos(String sedeIdFiltro, Boolean alertaStockBajo) {
        List<ProductoInventarioEntity> productos = productoRepository.findByActivoTrueOrderByNombreAsc();
        List<InventarioStockEntity> allStock = stockRepository.findAll();
        if (sedeIdFiltro == null || sedeIdFiltro.isBlank()) {
            // "Todas las sedes" del administrador general = todas las del negocio activo.
            allStock = allStock.stream().filter(s -> SecurityUtils.sedeEnNegocioActivo(s.getSedeId())).toList();
        }

        // shortcut: no hay stock por lote, se usa la entrada con vencimiento futuro más cercano; mejorar si se modela el saldo por lote
        String sedeFiltro = (sedeIdFiltro == null || sedeIdFiltro.isBlank()) ? null : sedeIdFiltro;
        Map<UUID, Object[]> fechasPorProducto = new HashMap<>();
        List<String> sedesDelNegocio = sedeFiltro == null ? SecurityUtils.sedesDelNegocioActivo() : null;
        if (sedesDelNegocio == null) {
            for (Object[] fila : movimientoRepository.resumenFechasPorProducto(LocalDate.now(ZONA_LIMA), sedeFiltro)) {
                fechasPorProducto.put((UUID) fila[0], fila);
            }
        } else {
            // Una consulta por sede del negocio; se combinan el próximo vencimiento más cercano y la entrega más reciente.
            for (String sede : sedesDelNegocio) {
                for (Object[] fila : movimientoRepository.resumenFechasPorProducto(LocalDate.now(ZONA_LIMA), sede)) {
                    fechasPorProducto.merge((UUID) fila[0], fila, InventarioService::combinarFechas);
                }
            }
        }

        Map<UUID, Map<String, Integer>> stockPorProducto = new HashMap<>();
        for (InventarioStockEntity s : allStock) {
            stockPorProducto
                    .computeIfAbsent(s.getProductoId(), k -> new HashMap<>())
                    .put(s.getSedeId(), s.getCantidad());
        }

        List<ProductoInventarioResponseDTO> resultado = new ArrayList<>();
        for (ProductoInventarioEntity prod : productos) {
            Map<String, Integer> stockMap = stockPorProducto.getOrDefault(prod.getId(), Collections.emptyMap());
            int stockTotal = stockMap.values().stream().mapToInt(Integer::intValue).sum();
            Object[] fechas = fechasPorProducto.get(prod.getId());

            boolean esAlerta;
            if (sedeIdFiltro != null && !sedeIdFiltro.isBlank()) {
                int stockSede = stockMap.getOrDefault(sedeIdFiltro, 0);
                esAlerta = (stockSede <= prod.getStockMinimo());
            } else {
                esAlerta = stockMap.isEmpty() || stockMap.values().stream().anyMatch(val -> val <= prod.getStockMinimo());
            }

            if (Boolean.TRUE.equals(alertaStockBajo) && !esAlerta) {
                continue;
            }

            resultado.add(ProductoInventarioResponseDTO.builder()
                    .id(prod.getId())
                    .codigo(prod.getCodigo())
                    .nombre(prod.getNombre())
                    .categoria(prod.getCategoria())
                    .unidad(prod.getUnidad())
                    .precioUnitario(prod.getPrecioUnitario())
                    .stockMinimo(prod.getStockMinimo())
                    .activo(prod.isActivo())
                    .stockPorSede(stockMap)
                    .stockTotal(stockTotal)
                    .alertaStockBajo(esAlerta)
                    .actualizadoEn(prod.getActualizadoEn())
                    .proximoVencimiento(fechas == null ? null : (LocalDate) fechas[1])
                    .ultimaEntrega(fechas == null ? null : (LocalDate) fechas[2])
                    .build());
        }

        return resultado;
    }

    @Transactional(readOnly = true)
    public ProductoInventarioResponseDTO obtenerProductoPorId(UUID id) {
        ProductoInventarioEntity prod = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        List<InventarioStockEntity> stocks = stockRepository.findByProductoId(id);
        Map<String, Integer> stockMap = stocks.stream()
                .collect(Collectors.toMap(InventarioStockEntity::getSedeId, InventarioStockEntity::getCantidad));
        int total = stockMap.values().stream().mapToInt(Integer::intValue).sum();
        boolean esAlerta = stockMap.isEmpty() || stockMap.values().stream().anyMatch(val -> val <= prod.getStockMinimo());

        return ProductoInventarioResponseDTO.builder()
                .id(prod.getId())
                .codigo(prod.getCodigo())
                .nombre(prod.getNombre())
                .categoria(prod.getCategoria())
                .unidad(prod.getUnidad())
                .precioUnitario(prod.getPrecioUnitario())
                .stockMinimo(prod.getStockMinimo())
                .activo(prod.isActivo())
                .stockPorSede(stockMap)
                .stockTotal(total)
                .alertaStockBajo(esAlerta)
                .actualizadoEn(prod.getActualizadoEn())
                .build();
    }

    @Transactional
    public ProductoInventarioResponseDTO crearProducto(ProductoInventarioCreateRequestDTO req, ClinicaUserPrincipal user) {
        String codigo = (req.getCodigo() == null || req.getCodigo().isBlank())
                ? siguienteCodigoDisponible()
                : req.getCodigo().trim().toUpperCase();
        if (productoRepository.existsByCodigoIgnoreCase(codigo)) {
            // Incluye insumos dados de baja: el código es único en todo el historial
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un insumo con el código: " + codigo);
        }

        ProductoInventarioEntity entity = ProductoInventarioEntity.builder()
                .codigo(codigo)
                .nombre(req.getNombre().trim())
                .categoria(req.getCategoria())
                .unidad(req.getUnidad())
                .precioUnitario(req.getPrecioUnitario())
                .stockMinimo(req.getStockMinimo() != null ? req.getStockMinimo() : 5)
                .activo(true)
                .build();

        ProductoInventarioEntity guardado = productoRepository.save(entity);

        registrarAuditoria(user, user != null ? user.sedeId() : null, "CREAR_PRODUCTO",
                "Creado insumo " + guardado.getCodigo() + " - " + guardado.getNombre());

        return obtenerProductoPorId(guardado.getId());
    }

    @Transactional
    public ProductoInventarioResponseDTO actualizarProducto(UUID id, ProductoInventarioUpdateRequestDTO req, ClinicaUserPrincipal user) {
        ProductoInventarioEntity prod = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        prod.setNombre(req.getNombre().trim());
        prod.setCategoria(req.getCategoria());
        prod.setUnidad(req.getUnidad());
        prod.setPrecioUnitario(req.getPrecioUnitario());
        prod.setStockMinimo(req.getStockMinimo() != null ? req.getStockMinimo() : 5);
        if (req.getActivo() != null) {
            prod.setActivo(req.getActivo());
        }
        prod.setActualizadoEn(java.time.OffsetDateTime.now());

        ProductoInventarioEntity guardado = productoRepository.save(prod);

        registrarAuditoria(user, user != null ? user.sedeId() : null, "ACTUALIZAR_PRODUCTO",
                "Actualizado insumo " + guardado.getCodigo() + " - " + guardado.getNombre());

        return obtenerProductoPorId(guardado.getId());
    }

    /** Código correlativo libre: MAT-001, MAT-002, ... (cuenta también los dados de baja). */
    private String siguienteCodigoDisponible() {
        long n = productoRepository.count() + 1;
        String codigo = String.format("MAT-%03d", n);
        while (productoRepository.existsByCodigoIgnoreCase(codigo)) {
            codigo = String.format("MAT-%03d", ++n);
        }
        return codigo;
    }

    /**
     * Baja lógica del insumo (activo = false). No se borra la fila para no romper las llaves
     * foráneas de stock, movimientos y transferencias históricas.
     */
    @Transactional
    public void darDeBajaProducto(UUID id, ClinicaUserPrincipal user) {
        ProductoInventarioEntity prod = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!prod.isActivo()) {
            return;
        }
        prod.setActivo(false);
        productoRepository.save(prod);
        registrarAuditoria(user, user != null ? user.sedeId() : null, "BAJA_PRODUCTO",
                "Dado de baja insumo " + prod.getCodigo() + " - " + prod.getNombre());
    }

    private static void validarActivo(ProductoInventarioEntity prod) {
        if (!prod.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El insumo " + prod.getNombre() + " fue dado de baja.");
        }
    }

    @Transactional
    public InventarioMovimientoResponseDTO registrarMovimiento(InventarioMovimientoRequestDTO req, ClinicaUserPrincipal user) {
        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (user.sedeId() != null && !user.sedeId().equalsIgnoreCase(req.getSedeId())) {
                throw new AccessDeniedException("No tiene permisos para registrar movimientos en una sede distinta a la suya.");
            }
        }

        if (req.getCantidad() == null || req.getCantidad() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser diferente de cero.");
        }

        ProductoInventarioEntity prod = productoRepository.findById(req.getProductoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        validarActivo(prod);

        int currentStock = stockRepository.findByProductoIdAndSedeId(req.getProductoId(), req.getSedeId())
                .map(InventarioStockEntity::getCantidad)
                .orElse(0);

        int delta;
        String tipoUpper = req.getTipo().toUpperCase();
        if ("SALIDA".equals(tipoUpper)) {
            if (req.getCantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad para salida debe ser mayor a cero.");
            }
            delta = -Math.abs(req.getCantidad());
            if (currentStock + delta < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("Stock insuficiente en sede %s para el insumo %s. Stock disponible: %d, solicitado: %d",
                                req.getSedeId(), prod.getNombre(), currentStock, req.getCantidad()));
            }
        } else if ("ENTRADA".equals(tipoUpper)) {
            if (req.getCantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad para entrada debe ser mayor a cero.");
            }
            delta = Math.abs(req.getCantidad());
        } else if ("AJUSTE".equals(tipoUpper)) {
            if (req.getMotivo() == null || req.getMotivo().trim().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio para ajustes de inventario.");
            }
            delta = req.getCantidad();
            if (currentStock + delta < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("El ajuste resultaría en stock negativo en sede %s. Stock disponible: %d, ajuste: %d",
                                req.getSedeId(), currentStock, delta));
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de movimiento no válido: " + req.getTipo());
        }

        InventarioMovimientoEntity movimiento = InventarioMovimientoEntity.builder()
                .productoId(req.getProductoId())
                .sedeId(req.getSedeId())
                .tipo(tipoUpper)
                .cantidad(delta)
                .lote(req.getLote())
                .fechaVencimiento(req.getFechaVencimiento())
                .fechaEntrega(req.getFechaEntrega())
                .proveedor(req.getProveedor())
                .motivo(req.getMotivo())
                .registradoPor(user != null ? user.id() : null)
                .build();

        InventarioMovimientoEntity guardado = movimientoRepository.save(movimiento);

        registrarAuditoria(user, req.getSedeId(), "MOVIMIENTO_INVENTARIO",
                String.format("Movimiento %s de %d unidades en %s para %s (Stock anterior: %d, nuevo estimado: %d)",
                        tipoUpper, Math.abs(delta), req.getSedeId(), prod.getCodigo(), currentStock, currentStock + delta));

        return InventarioMovimientoResponseDTO.builder()
                .id(guardado.getId())
                .productoId(guardado.getProductoId())
                .productoNombre(prod.getNombre())
                .sedeId(guardado.getSedeId())
                .tipo(guardado.getTipo())
                .cantidad(guardado.getCantidad())
                .lote(guardado.getLote())
                .fechaVencimiento(guardado.getFechaVencimiento())
                .fechaEntrega(guardado.getFechaEntrega())
                .proveedor(guardado.getProveedor())
                .motivo(guardado.getMotivo())
                .registradoPor(guardado.getRegistradoPor())
                .creadoEn(guardado.getCreadoEn())
                .build();
    }

    @Transactional(readOnly = true)
    public List<InventarioMovimientoResponseDTO> listarMovimientos(UUID productoId, String sedeId) {
        List<InventarioMovimientoEntity> entities;
        if (productoId != null) {
            entities = movimientoRepository.findByProductoIdOrderByCreadoEnDesc(productoId);
        } else if (sedeId != null && !sedeId.isBlank()) {
            entities = movimientoRepository.findBySedeIdOrderByCreadoEnDesc(sedeId);
        } else {
            entities = movimientoRepository.findAll().stream()
                    .filter(m -> SecurityUtils.sedeEnNegocioActivo(m.getSedeId()))
                    .toList();
        }

        Map<UUID, String> nombresMap = productoRepository.findAll().stream()
                .collect(Collectors.toMap(ProductoInventarioEntity::getId, ProductoInventarioEntity::getNombre, (a, b) -> a));

        return entities.stream().map(m -> InventarioMovimientoResponseDTO.builder()
                .id(m.getId())
                .productoId(m.getProductoId())
                .productoNombre(nombresMap.getOrDefault(m.getProductoId(), "Insumo desconocido"))
                .sedeId(m.getSedeId())
                .tipo(m.getTipo())
                .cantidad(m.getCantidad())
                .lote(m.getLote())
                .fechaVencimiento(m.getFechaVencimiento())
                .fechaEntrega(m.getFechaEntrega())
                .proveedor(m.getProveedor())
                .motivo(m.getMotivo())
                .registradoPor(m.getRegistradoPor())
                .creadoEn(m.getCreadoEn())
                .build()
        ).collect(Collectors.toList());
    }

    /**
     * El stock de origen se descuenta automáticamente por el trigger DB {@code transferencias_procesar_trg}
     * (BEFORE INSERT ON transferencias_inventario, estado='EN_TRANSITO') que inserta un movimiento
     * TRANSFERENCIA_SALIDA en inventario_movimientos, el cual a su vez actualiza inventario_stock
     * vía el trigger {@code inventario_movimiento_aplica}.
     */
    @Transactional
    public TransferenciaResponseDTO solicitarTransferencia(TransferenciaCreateRequestDTO req, ClinicaUserPrincipal user) {
        if (req.getSedeOrigenId().equalsIgnoreCase(req.getSedeDestinoId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede de origen y la de destino no pueden ser la misma.");
        }

        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (user.sedeId() != null &&
                    !user.sedeId().equalsIgnoreCase(req.getSedeOrigenId()) &&
                    !user.sedeId().equalsIgnoreCase(req.getSedeDestinoId())) {
                throw new AccessDeniedException("No tiene permisos para gestionar transferencias fuera de su sede asignada.");
            }
        }

        ProductoInventarioEntity prod = productoRepository.findById(req.getProductoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        validarActivo(prod);

        int currentOriginStock = stockRepository.findByProductoIdAndSedeId(req.getProductoId(), req.getSedeOrigenId())
                .map(InventarioStockEntity::getCantidad)
                .orElse(0);

        if (currentOriginStock < req.getCantidad()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Stock insuficiente en sede de origen %s (%s). Stock actual: %d, requerido para traslado: %d",
                            req.getSedeOrigenId(), prod.getNombre(), currentOriginStock, req.getCantidad()));
        }

        if (req.getCantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad a transferir debe ser mayor a cero.");
        }

        TransferenciaInventarioEntity entity = TransferenciaInventarioEntity.builder()
                .productoId(req.getProductoId())
                .cantidad(req.getCantidad())
                .sedeOrigenId(req.getSedeOrigenId())
                .sedeDestinoId(req.getSedeDestinoId())
                .estado("EN_TRANSITO")
                .motivo(req.getMotivo())
                .solicitadoPor(user != null ? user.id() : null)
                .build();

        TransferenciaInventarioEntity guardada = transferenciaRepository.save(entity);

        registrarAuditoria(user, req.getSedeOrigenId(), "SOLICITAR_TRANSFERENCIA",
                String.format("Transferencia iniciada de %d uds de %s desde %s hacia %s (stock origen previo: %d)",
                        req.getCantidad(), prod.getCodigo(), req.getSedeOrigenId(), req.getSedeDestinoId(), currentOriginStock));

        return toTransferenciaDTO(guardada, prod.getNombre(), user != null ? user.nombre() : "Usuario");
    }

    @Transactional
    public TransferenciaResponseDTO recibirTransferencia(UUID transferenciaId, ClinicaUserPrincipal user) {
        TransferenciaInventarioEntity trans = transferenciaRepository.findById(transferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transferencia no encontrada"));

        if (!"EN_TRANSITO".equalsIgnoreCase(trans.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La transferencia no se encuentra en estado EN_TRANSITO (estado actual: " + trans.getEstado() + ")");
        }

        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (user.sedeId() != null && !user.sedeId().equalsIgnoreCase(trans.getSedeDestinoId())) {
                throw new AccessDeniedException("Solo el personal de la sede de destino (" + trans.getSedeDestinoId() + ") o un administrador general pueden confirmar la recepción.");
            }
        }

        trans.setEstado("RECIBIDO");
        trans.setRecibidoEn(java.time.OffsetDateTime.now());
        TransferenciaInventarioEntity guardada = transferenciaRepository.save(trans);

        ProductoInventarioEntity prod = productoRepository.findById(trans.getProductoId()).orElse(null);
        String prodNombre = prod != null ? prod.getNombre() : "Insumo";

        registrarAuditoria(user, trans.getSedeDestinoId(), "RECIBIR_TRANSFERENCIA",
                String.format("Transferencia %s recibida en %s (%d uds de %s)",
                        trans.getId(), trans.getSedeDestinoId(), trans.getCantidad(), prodNombre));

        return toTransferenciaDTO(guardada, prodNombre, user != null ? user.nombre() : "Usuario");
    }

    @Transactional
    public TransferenciaResponseDTO cancelarTransferencia(UUID transferenciaId, ClinicaUserPrincipal user) {
        TransferenciaInventarioEntity trans = transferenciaRepository.findById(transferenciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transferencia no encontrada"));

        if ("RECIBIDO".equalsIgnoreCase(trans.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cancelar una transferencia que ya fue recibida.");
        }
        if ("CANCELADO".equalsIgnoreCase(trans.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La transferencia ya se encuentra cancelada.");
        }

        if (user != null && !"ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (user.sedeId() != null && !user.sedeId().equalsIgnoreCase(trans.getSedeOrigenId())) {
                throw new AccessDeniedException("Solo la sede de origen (" + trans.getSedeOrigenId() + ") o un administrador general pueden cancelar la transferencia.");
            }
        }

        trans.setEstado("CANCELADO");
        TransferenciaInventarioEntity guardada = transferenciaRepository.save(trans);

        ProductoInventarioEntity prod = productoRepository.findById(trans.getProductoId()).orElse(null);
        String prodNombre = prod != null ? prod.getNombre() : "Insumo";

        registrarAuditoria(user, trans.getSedeOrigenId(), "CANCELAR_TRANSFERENCIA",
                String.format("Transferencia %s cancelada desde %s", trans.getId(), trans.getSedeOrigenId()));

        return toTransferenciaDTO(guardada, prodNombre, user != null ? user.nombre() : "Usuario");
    }

    @Transactional(readOnly = true)
    public List<TransferenciaResponseDTO> listarTransferencias(String sedeIdFiltro) {
        List<TransferenciaInventarioEntity> transferencias;
        if (sedeIdFiltro != null && !sedeIdFiltro.isBlank()) {
            transferencias = transferenciaRepository.findBySede(sedeIdFiltro);
        } else {
            transferencias = transferenciaRepository.findAllByOrderByCreadoEnDesc();
        }

        Map<UUID, String> nombresProd = productoRepository.findAll().stream()
                .collect(Collectors.toMap(ProductoInventarioEntity::getId, ProductoInventarioEntity::getNombre, (a, b) -> a));

        Map<UUID, String> nombresUsuarios = transferencias.stream()
                .map(t -> t.getSolicitadoPor())
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> consultaUsuarioModuloApi.buscarPorId(id))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(u -> u.id(), u -> u.nombre(), (a, b) -> a));

        return transferencias.stream().map(t -> {
            String prodNombre = nombresProd.getOrDefault(t.getProductoId(), "Insumo");
            String solicitante = t.getSolicitadoPor() != null ? nombresUsuarios.getOrDefault(t.getSolicitadoPor(), "Personal") : "Personal";
            return toTransferenciaDTO(t, prodNombre, solicitante);
        }).collect(Collectors.toList());
    }

    private TransferenciaResponseDTO toTransferenciaDTO(TransferenciaInventarioEntity t, String prodNombre, String solicitante) {
        String fechaStr = t.getCreadoEn() != null
                ? t.getCreadoEn().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : "";

        return TransferenciaResponseDTO.builder()
                .id(t.getId())
                .productoId(t.getProductoId())
                .productoNombre(prodNombre)
                .cantidad(t.getCantidad())
                .sedeOrigenId(t.getSedeOrigenId())
                .sedeOrigenNombre(t.getSedeOrigenId())
                .sedeDestinoId(t.getSedeDestinoId())
                .sedeDestinoNombre(t.getSedeDestinoId())
                .estado(t.getEstado())
                .motivo(t.getMotivo())
                .solicitadoPor(t.getSolicitadoPor())
                .usuarioSolicita(solicitante)
                .fecha(fechaStr)
                .creadoEn(t.getCreadoEn())
                .recibidoEn(t.getRecibidoEn())
                .build();
    }

    private void registrarAuditoria(ClinicaUserPrincipal user, String sedeId, String accion, String detalle) {
        try {
            auditoriaRepository.save(AuditoriaLogEntity.builder()
                    .usuarioId(user != null ? user.id() : null)
                    .usuarioNombre(user != null ? user.nombre() : "Sistema")
                    .rol(user != null ? user.rol() : "ANONYMOUS")
                    .sedeId(sedeId)
                    .modulo("INVENTARIO")
                    .accion(accion)
                    .tabla("productos_inventario")
                    .detalle(detalle)
                    .ip(SecurityUtils.getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar la auditoría de inventario: {}", e.getMessage());
        }
    }
}
