package com.odontomax.identidad.api;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Objects;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static ClinicaUserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ClinicaUserPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * Valida si el usuario actual tiene permiso para acceder o modificar datos de una sede específica.
     * ADMIN_GENERAL tiene permiso irrestricto sobre todas las sedes.
     * ADMIN_SEDE, RECEPCION y ODONTOLOGO solo pueden operar en su sede asignada.
     */
    public static void validarAccesoSede(String sedeIdSolicitada) {
        ClinicaUserPrincipal user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("No se encuentra autenticado en el sistema.");
        }

        // El Administrador General tiene acceso omnicanal a todas las sedes del negocio activo
        if ("ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (sedeIdSolicitada != null && !sedeIdSolicitada.isBlank() && !sedeEnNegocioActivo(sedeIdSolicitada.trim())) {
                throw new AccessDeniedException(String.format(
                        "Acceso Denegado: La sede '%s' no pertenece al negocio activo (%s).",
                        sedeIdSolicitada, negocioActivo()
                ));
            }
            return;
        }

        // Si el usuario no es ADMIN_GENERAL y no tiene sedeId asignada, denegar acceso
        if (user.sedeId() == null || user.sedeId().isBlank()) {
            throw new AccessDeniedException(String.format(
                    "Acceso Denegado: Su usuario (%s) no tiene una sede asignada en el sistema.",
                    user.email()
            ));
        }

        // Si la petición no especifica sede o es nula, denegar acceso (evitar omisión de validación)
        if (sedeIdSolicitada == null || sedeIdSolicitada.isBlank()) {
            throw new AccessDeniedException(
                    "Acceso Denegado: Debe especificar una sede válida para realizar esta operación."
            );
        }

        // Bloquear acceso cruzado a otras sedes
        if (!user.sedeId().equalsIgnoreCase(sedeIdSolicitada.trim())) {
            throw new AccessDeniedException(String.format(
                    "Acceso Denegado: Su usuario (%s) está asignado a '%s' y no tiene permisos para acceder a la sede '%s'.",
                    user.email(), user.sedeId(), sedeIdSolicitada
            ));
        }
    }

    /**
     * Resuelve la sede efectiva para la consulta.
     * Si no se especifica sede y no es ADMIN_GENERAL, aplica la sede del usuario.
     */
    public static String resolverSedeEfectiva(String sedeIdParam) {
        ClinicaUserPrincipal user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("No se encuentra autenticado en el sistema.");
        }

        if ("ADMIN_GENERAL".equalsIgnoreCase(user.rol())) {
            if (sedeIdParam != null && !sedeIdParam.isBlank()) {
                validarAccesoSede(sedeIdParam);
                return sedeIdParam.trim();
            }
            return null;
        }

        if (user.sedeId() == null || user.sedeId().isBlank()) {
            throw new AccessDeniedException(String.format(
                    "Acceso Denegado: Su usuario (%s) no tiene una sede asignada en el sistema.",
                    user.email()
            ));
        }

        // Para usuarios de sede, forzar su sede si no mandaron nada o validar la que mandaron
        if (sedeIdParam == null || sedeIdParam.isBlank()) {
            return user.sedeId();
        }

        validarAccesoSede(sedeIdParam);
        return sedeIdParam.trim();
    }

    public static String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getRemoteAddr();
        return (ip != null && !ip.isBlank()) ? ip.trim() : "127.0.0.1";
    }

    public static String getClientIp() {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return getClientIp(servletAttrs.getRequest());
        }
        return "127.0.0.1";
    }

    // ---- Negocio activo (Villadent / Villamax) ----

    public static final String HEADER_NEGOCIO = "X-Negocio";
    private static volatile NegocioSedesProvider proveedorNegocio;

    /** Quien conoce las sedes de cada negocio; lo registra el módulo de sedes al arrancar. */
    public interface NegocioSedesProvider {
        List<String> sedesDelNegocio(String negocio);
    }

    public static void registrarProveedorNegocio(NegocioSedesProvider proveedor) {
        proveedorNegocio = proveedor;
    }

    /**
     * Negocio sobre el que el administrador general está trabajando (header X-Negocio): VILLADENT o VILLAMAX.
     * Solo se aplica al ADMIN_GENERAL, que es el único que puede consultar "todas las sedes"; para los demás roles es
     * nulo porque su alcance ya lo fija su sede. Nulo también si no hay petición web o el header no viene.
     * Un valor desconocido es un error de petición (400).
     */
    public static String negocioActivo() {
        ClinicaUserPrincipal user = getCurrentUser();
        return (user != null && "ADMIN_GENERAL".equalsIgnoreCase(user.rol())) ? negocioDeLaPeticion() : null;
    }

    /**
     * Negocio cuyo catálogo se está consultando: el del header X-Negocio para cualquier rol (también la web pública) o
     * VILLADENT si no viene. Solo elige qué catálogo se lista; no autoriza nada ni amplía el alcance de nadie.
     */
    public static String negocioDelCatalogo() {
        String negocio = negocioDeLaPeticion();
        return negocio != null ? negocio : "VILLADENT";
    }

    public static String negocioDeLaPeticion() {
        if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attrs)) {
            return null;
        }
        String valor = attrs.getRequest().getHeader(HEADER_NEGOCIO);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String negocio = valor.trim().toUpperCase(java.util.Locale.ROOT);
        if (!negocio.equals("VILLADENT") && !negocio.equals("VILLAMAX")) {
            throw new IllegalArgumentException("El negocio indicado no es válido: use VILLADENT o VILLAMAX.");
        }
        return negocio;
    }

    /** Sedes del negocio activo, o nulo si no hay filtro por negocio. Nunca devuelve una lista vacía. */
    public static List<String> sedesDelNegocioActivo() {
        String negocio = negocioActivo();
        if (negocio == null || proveedorNegocio == null) {
            return null;
        }
        List<String> sedes = proveedorNegocio.sedesDelNegocio(negocio);
        return (sedes == null || sedes.isEmpty()) ? List.of("__sin_sedes__") : sedes;
    }

    /** true si la sede pertenece al negocio activo o si no hay filtro por negocio. */
    public static boolean sedeEnNegocioActivo(String sedeId) {
        List<String> sedes = sedesDelNegocioActivo();
        return sedes == null || sedes.contains(sedeId);
    }
}
