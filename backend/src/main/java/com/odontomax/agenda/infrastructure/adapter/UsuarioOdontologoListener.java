package com.odontomax.agenda.infrastructure.adapter;

import com.odontomax.agenda.application.service.EspecialistaApplicationService;
import com.odontomax.identidad.api.event.UsuarioEstadoModificado;
import com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsuarioOdontologoListener {

    private final EspecialistaApplicationService especialistaService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onUsuarioOdontologoRegistrado(UsuarioOdontologoRegistrado event) {
        log.info("Sincronizando perfil de doctor para nuevo usuario odontólogo ID: {}", event.usuarioId());
        especialistaService.asegurarPerfilDeOdontologo(
                event.usuarioId(),
                event.nombre(),
                event.email(),
                event.sedeId(),
                event.especialidad()
        );
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onUsuarioEstadoModificado(UsuarioEstadoModificado event) {
        log.info("Sincronizando estado de doctor para usuario ID: {} (activo: {})", event.usuarioId(), event.activo());
        especialistaService.sincronizarEstadoConUsuario(event.usuarioId(), event.activo());
    }
}
