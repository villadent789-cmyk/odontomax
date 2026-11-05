package com.odontomax.auditoria.api;

import java.util.UUID;

/** Datos de una entrada de auditoría, ya resueltos por el módulo que la emite. */
public record RegistroAuditoria(
        UUID usuarioId,
        String usuarioNombre,
        String rol,
        String sedeId,
        String modulo,
        String accion,
        String tabla,
        String registroId,
        String detalle,
        String ip
) {
}
