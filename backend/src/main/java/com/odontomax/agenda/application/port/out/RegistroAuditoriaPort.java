package com.odontomax.agenda.application.port.out;

public interface RegistroAuditoriaPort {
    void registrar(String accion, String modulo, String sedeId, String detalle);
    void registrar(String modulo, String accion, String tabla, String registroId, String detalle);
}
