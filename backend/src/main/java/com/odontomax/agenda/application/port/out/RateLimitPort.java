package com.odontomax.agenda.application.port.out;

public interface RateLimitPort {

    /** Ventana corta de 10 minutos. */
    boolean isRateLimited(String key, int maxRequests);

    /** Ventana a elegir (p. ej. 24 horas para topes diarios). */
    boolean isRateLimited(String key, int maxRequests, long windowSeconds);
}
