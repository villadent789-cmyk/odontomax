package com.odontomax.auditoria.api;

/**
 * Punto único de escritura en la bitácora de auditoría ({@code auditoria_log}).
 *
 * La API es neutra: guarda los 10 campos tal como llegan, sin valores por defecto ni captura de
 * excepciones. Los defaults y la gestión de errores son responsabilidad del adaptador de cada módulo.
 */
public interface RegistrarAuditoriaApi {

    void registrar(RegistroAuditoria registro);
}
