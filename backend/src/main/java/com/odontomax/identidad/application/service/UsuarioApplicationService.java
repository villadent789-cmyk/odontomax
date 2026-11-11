package com.odontomax.identidad.application.service;

import com.odontomax.application.dto.request.UsuarioCreateRequestDTO;
import com.odontomax.application.dto.request.UsuarioResetPasswordDTO;
import com.odontomax.application.dto.request.UsuarioUpdateRequestDTO;
import com.odontomax.application.dto.response.UsuarioResponseDTO;
import com.odontomax.identidad.api.ClinicaUserPrincipal;
import com.odontomax.identidad.api.event.UsuarioEstadoModificado;
import com.odontomax.identidad.api.event.UsuarioOdontologoRegistrado;
import com.odontomax.identidad.application.port.in.ConsultarUsuarioUseCase;
import com.odontomax.identidad.application.port.in.GestionarUsuarioUseCase;
import com.odontomax.identidad.application.port.out.JwtUserCachePort;
import com.odontomax.identidad.application.port.out.PasswordHistorialRepository;
import com.odontomax.identidad.application.port.out.RegistroAuditoriaPort;
import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioApplicationService implements ConsultarUsuarioUseCase, GestionarUsuarioUseCase {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistroAuditoriaPort registroAuditoria;
    private final PasswordHistorialRepository passwordHistorialRepository;
    // Cuentas ODONTOLOGO = doctores de la agenda: eventos sincronos desacoplados
    private final ApplicationEventPublisher eventPublisher;
    private final JwtUserCachePort jwtUserCache;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(UsuarioApplicationService::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioCreateRequestDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        String emailClean = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.findByEmailIgnoreCase(emailClean).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con el correo: " + emailClean);
        }

        String rol = request.getRol().trim().toUpperCase();
        String sedeId = request.getSedeId() != null && !request.getSedeId().isBlank() ? request.getSedeId().trim() : null;

        // Sede donde atiende como doctor: la de la cuenta o, si es Administrador General (sin sede propia), la elegida
        String sedeDelDoctor = sedeId;

        // Validar reglas de sede por rol
        sedeId = Usuario.resolverSedeSegunRol(rol, sedeId);
        List<String> adicionales = Usuario.normalizarRolesAdicionales(rol, request.getRolesAdicionales());
        if (adicionales.contains("ODONTOLOGO") && sedeDelDoctor == null) {
            throw new IllegalArgumentException("Elija la sede donde atenderá como odontólogo.");
        }

        Usuario nuevo = Usuario.builder()
                .nombre(request.getNombre().trim())
                .email(emailClean)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(rol)
                .rolesAdicionales(adicionales)
                .sedeId(sedeId)
                .activo(true)
                .build();

        Usuario guardado = usuarioRepository.save(nuevo);

        passwordHistorialRepository.guardar(guardado.getId(), guardado.getPasswordHash());

        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                "CREAR_USUARIO",
                "usuarios",
                guardado.getId().toString(),
                String.format("Usuario creado: %s | Rol: %s%s | Sede: %s", guardado.getEmail(), guardado.getRol(), detalleFunciones(guardado), guardado.getSedeId()),
                clientIp
        );

        UsuarioResponseDTO response = toResponse(guardado);
        if ("ODONTOLOGO".equals(rol) || guardado.getRolesAdicionales().contains("ODONTOLOGO")) {
            eventPublisher.publishEvent(new UsuarioOdontologoRegistrado(response.getId(), request.getNombre().trim(), emailClean, sedeDelDoctor, request.getEspecialidad()));
        }

        log.info("Usuario creado exitosamente con ID {} por ID {}", response.getId(), adminPrincipal != null ? adminPrincipal.id() : "SISTEMA");
        return response;
    }

    @Override
    @Transactional
    public UsuarioResponseDTO actualizarUsuario(UUID id, UsuarioUpdateRequestDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        Usuario usuario = buscar(id);

        String rol = request.getRol().trim().toUpperCase();
        String sedeId = request.getSedeId() != null && !request.getSedeId().isBlank() ? request.getSedeId().trim() : null;

        // Evita que el administrador en sesión se quite a sí mismo el acceso (rol o estado)
        usuario.verificarAutoproteccionAlEditar(adminPrincipal != null ? adminPrincipal.id() : null, rol, request.getActivo());

        String sedeDelDoctor = sedeId;
        sedeId = Usuario.resolverSedeSegunRol(rol, sedeId);
        if (Usuario.normalizarRolesAdicionales(rol, request.getRolesAdicionales()).contains("ODONTOLOGO") && sedeDelDoctor == null) {
            throw new IllegalArgumentException("Elija la sede donde atenderá como odontólogo.");
        }

        usuario.actualizarDatos(request.getNombre().trim(), rol, request.getRolesAdicionales(), sedeId, request.getActivo());

        Usuario actualizado = usuarioRepository.save(usuario);
        UsuarioResponseDTO response = toResponse(actualizado);

        if ("ODONTOLOGO".equals(rol) || actualizado.getRolesAdicionales().contains("ODONTOLOGO")) {
            eventPublisher.publishEvent(new UsuarioOdontologoRegistrado(id, request.getNombre().trim(), response.getEmail(), sedeDelDoctor, request.getEspecialidad()));
        }
        if (request.getActivo() != null) {
            eventPublisher.publishEvent(new UsuarioEstadoModificado(id, Boolean.TRUE.equals(request.getActivo())));
        }

        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                "ACTUALIZAR_USUARIO",
                "usuarios",
                actualizado.getId().toString(),
                String.format("Usuario actualizado: %s | Rol: %s%s | Sede: %s | Activo: %s",
                        actualizado.getEmail(), actualizado.getRol(), detalleFunciones(actualizado), actualizado.getSedeId(), actualizado.isActivo()),
                clientIp
        );

        return response;
    }

    @Override
    @Transactional
    public UsuarioResponseDTO cambiarEstado(UUID id, boolean activo, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        Usuario usuario = buscar(id);

        if (adminPrincipal != null && adminPrincipal.id().equals(id) && !activo) {
            throw new IllegalArgumentException("No puede desactivar su propia cuenta de administrador en sesión.");
        }

        usuario.setActivo(activo);
        Usuario actualizado = usuarioRepository.save(usuario);
        eventPublisher.publishEvent(new UsuarioEstadoModificado(id, activo));

        String accion = activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO";
        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                accion,
                "usuarios",
                actualizado.getId().toString(),
                String.format("Usuario %s marcado como %s", actualizado.getEmail(), activo ? "ACTIVO" : "INACTIVO"),
                clientIp
        );

        return toResponse(actualizado);
    }

    @Override
    @Transactional
    public Map<String, Object> resetearPassword(UUID id, UsuarioResetPasswordDTO request, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        Usuario usuario = buscar(id);

        if (passwordEncoder.matches(request.getNewPassword(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede ser idéntica a la contraseña actual.");
        }

        if (IdentidadDtos.coincideConHistorial(passwordEncoder, request.getNewPassword(),
                passwordHistorialRepository.findUltimosTresHashes(id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña no puede coincidir con ninguna de las últimas 3 contraseñas utilizadas.");
        }

        passwordHistorialRepository.guardar(usuario.getId(), usuario.getPasswordHash());

        // Los JWT del usuario emitidos hasta ahora dejan de valer al instante (B-008)
        usuario.cambiarPassword(passwordEncoder.encode(request.getNewPassword()));
        usuarioRepository.save(usuario);
        IdentidadDtos.desalojarCacheJwt(jwtUserCache, usuario);

        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                "RESETEAR_PASSWORD",
                "usuarios",
                usuario.getId().toString(),
                "Contraseña restablecida exitosamente para " + usuario.getEmail(),
                clientIp
        );

        return Map.of(
                "status", 200,
                "mensaje", "Contraseña restablecida correctamente para " + usuario.getEmail()
        );
    }

    @Override
    @Transactional
    public Map<String, Object> eliminarUsuario(UUID id, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        Usuario usuario = buscar(id);

        if (adminPrincipal != null && adminPrincipal.id().equals(id)) {
            throw new IllegalArgumentException("No puede eliminar su propia cuenta de administrador en sesión.");
        }

        String email = usuario.getEmail();

        // Se decide ANTES de borrar: capturar la violación de integridad dentro de esta misma transacción
        // no sirve, porque queda marcada como rollback-only y el commit final fallaría (500).
        if (usuarioRepository.tieneRegistrosVinculados(id)) {
            // Registros clínicos/contables vinculados: se conserva la cuenta por legalidad médica (Ley 29733)
            log.warn("El usuario ID {} tiene registros históricos vinculados. Aplicando baja inmediata (inactivación).", id);
            usuario.setActivo(false);
            usuarioRepository.save(usuario);
            eventPublisher.publishEvent(new UsuarioEstadoModificado(id, false));

            registroAuditoria.registrarOperacionUsuarios(
                    adminPrincipal,
                    "USUARIOS",
                    "BAJA_USUARIO_LOGICO",
                    "usuarios",
                    id.toString(),
                    "Usuario " + email + " dado de baja por registros clínicos/contables históricos vinculados",
                    clientIp
            );

            return Map.of(
                    "status", 200,
                    "tipo", "LOGICO",
                    "mensaje", "El usuario tiene registros históricos vinculados (citas, historias clínicas o pagos). Por normativa de salud y seguridad legal, fue dado de baja y desactivado inmediatamente del sistema."
            );
        }

        // Borrado físico (PostgreSQL / Supabase); el historial de contraseñas y tokens se borra en cascada.
        // El adaptador hace flush inmediato: la integridad se comprueba aquí y no en el commit.
        usuarioRepository.eliminar(id);

        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                "ELIMINAR_USUARIO_FISICO",
                "usuarios",
                id.toString(),
                "Eliminado físicamente de la base de datos el usuario: " + email,
                clientIp
        );

        return Map.of(
                "status", 200,
                "tipo", "FISICO",
                "mensaje", "Usuario " + email + " eliminado exitosamente de la base de datos."
        );
    }

    @Override
    @Transactional
    public Map<String, Object> restablecer2FA(UUID id, ClinicaUserPrincipal adminPrincipal, String clientIp) {
        Usuario usuario = buscar(id);
        usuario.verificarRestablecimientoTotpPorOtro(adminPrincipal != null ? adminPrincipal.id() : null);

        usuario.restablecerTotpPorAdministrador();
        usuarioRepository.save(usuario);
        IdentidadDtos.desalojarCacheJwt(jwtUserCache, usuario);

        registroAuditoria.registrarOperacionUsuarios(
                adminPrincipal,
                "USUARIOS",
                "RESTABLECER_2FA",
                "usuarios",
                usuario.getId().toString(),
                "2FA restablecido por un administrador para " + usuario.getEmail()
                        + " (secreto y códigos de respaldo eliminados, sesiones invalidadas)",
                clientIp
        );

        return Map.of(
                "status", 200,
                "mensaje", "2FA restablecido correctamente para " + usuario.getEmail()
        );
    }

    private Usuario buscar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
    }

    /** Texto de auditoría de las funciones adicionales; vacío si no tiene (el detalle queda como siempre). */
    private static String detalleFunciones(Usuario usuario) {
        return usuario.getRolesAdicionales().isEmpty() ? "" : " (+ " + String.join(", ", usuario.getRolesAdicionales()) + ")";
    }

    private static UsuarioResponseDTO toResponse(Usuario usuario) {
        // Las sedes se administran en BD: el frontend resuelve el nombre a partir del id (GET /sedes)
        String sedeNombre = usuario.getSedeId() != null ? usuario.getSedeId() : "Todas las Sedes / General";

        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .rolesAdicionales(usuario.getRolesAdicionales())
                .sedeId(usuario.getSedeId())
                .sedeNombre(sedeNombre)
                .activo(usuario.isActivo())
                .ultimoAcceso(usuario.getUltimoAcceso())
                .creadoEn(usuario.getCreadoEn())
                .build();
    }
}
