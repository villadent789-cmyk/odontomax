package com.odontomax.identidad.domain.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reglas de la recuperación de contraseña por correo. */
public final class PoliticaRecuperacionPassword {

    /** Solicitudes atendidas por cada correo + IP dentro de la ventana; por encima no se envía correo. */
    public static final int MAX_SOLICITUDES = 3;

    /**
     * Solicitudes atendidas por IP (IPv6: por prefijo /64) dentro de la ventana, se comprueba antes que el límite por
     * correo + IP (revisión BL-4). 20 cada 15 min deja margen a una oficina detrás de un NAT (varias personas, cada una
     * con sus 3 intentos) e impide que una sola IP cree miles de claves con correos aleatorios.
     */
    public static final int MAX_SOLICITUDES_POR_IP = 20;

    public static final Duration VENTANA = Duration.ofMinutes(15);

    private PoliticaRecuperacionPassword() {
    }

    /** Clave del límite: correo normalizado + IP de origen. */
    public static String claveLimite(String emailNormalizado, String ip) {
        return emailNormalizado + "|" + ipNormalizada(ip);
    }

    /** Clave del límite por origen: la IPv4 completa o el prefijo /64 de una IPv6. */
    public static String claveIp(String ip) {
        String normalizada = ipNormalizada(ip);
        if (!normalizada.contains(":")) {
            return "ip|" + normalizada;
        }
        String prefijo = prefijoIpv6(normalizada);
        return "ip6|" + (prefijo != null ? prefijo + "::/64" : normalizada);
    }

    private static String ipNormalizada(String ip) {
        return ip != null && !ip.isBlank() ? ip.trim() : "unknown";
    }

    /** Los 4 primeros grupos (64 bits) de una IPv6 en forma canónica; nulo si no es una IPv6 válida. */
    private static String prefijoIpv6(String ip) {
        String sinZona = ip.contains("%") ? ip.substring(0, ip.indexOf('%')) : ip;
        if (sinZona.startsWith("[") && sinZona.endsWith("]")) {
            sinZona = sinZona.substring(1, sinZona.length() - 1);
        }
        int dobles = sinZona.indexOf("::");
        if (dobles != sinZona.lastIndexOf("::")) {
            return null;
        }
        List<String> grupos;
        if (dobles >= 0) {
            List<String> cabeza = partir(sinZona.substring(0, dobles));
            List<String> cola = partir(sinZona.substring(dobles + 2));
            int faltan = 8 - cabeza.size() - cola.size();
            if (faltan < 1) {
                return null;
            }
            grupos = new ArrayList<>(cabeza);
            for (int i = 0; i < faltan; i++) {
                grupos.add("0");
            }
            grupos.addAll(cola);
        } else {
            grupos = partir(sinZona);
        }
        if (grupos.size() != 8 && !(grupos.size() == 7 && grupos.get(6).contains("."))) {
            return null; // la parte final puede ser una IPv4 incrustada; para el /64 solo importan los 4 primeros
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String g = grupos.get(i);
            if (!g.matches("[0-9a-fA-F]{1,4}")) {
                return null;
            }
            if (i > 0) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(Integer.parseInt(g, 16)));
        }
        return sb.toString();
    }

    private static List<String> partir(String s) {
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(":", -1)));
    }
}
