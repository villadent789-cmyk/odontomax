package com.odontomax.sedes.api;

/** Datos mínimos de una sede que otros módulos necesitan para validar operaciones (B-023). */
public record SedeResumen(String id, boolean activa, int sillones) {
}
