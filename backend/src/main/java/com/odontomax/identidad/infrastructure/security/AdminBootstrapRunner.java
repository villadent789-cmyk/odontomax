package com.odontomax.identidad.infrastructure.security;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final SpringDataUsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:${admin.bootstrap.email:}}")
    private String bootstrapEmail;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:${admin.bootstrap.password:}}")
    private String bootstrapPassword;

    /** Nombre del primer dueño (p. ej. "Max"); si no se indica, "Administrador General". */
    @Value("${ADMIN_BOOTSTRAP_NOMBRE:${admin.bootstrap.nombre:Administrador General}}")
    private String bootstrapNombre;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Inicialización de seguridad: server.forward-headers-strategy=native activo. Detección de IP configurada para proxy inverso (Render/Cloudflare).");

        if (bootstrapEmail != null && !bootstrapEmail.isBlank()
                && bootstrapPassword != null && !bootstrapPassword.isBlank()) {
            
            // Regla de seguridad estricta: AdminBootstrapRunner SOLO crea el admin si la tabla usuarios está vacía.
            // Nunca resetea contraseñas ni reactiva cuentas existentes.
            long totalUsuarios = usuarioRepository.count();
            if (totalUsuarios > 0) {
                log.info("Tabla usuarios ya contiene registros ({} usuario(s)). Omitiendo bootstrap de administrador inicial.", totalUsuarios);
                return;
            }

            String emailClean = bootstrapEmail.trim().toLowerCase();
            log.info("Tabla usuarios vacía. Creando usuario inicial ADMIN_GENERAL ...");
            UsuarioEntity admin = UsuarioEntity.builder()
                    .nombre(bootstrapNombre == null || bootstrapNombre.isBlank() ? "Administrador General" : bootstrapNombre.trim())
                    .email(emailClean)
                    .passwordHash(passwordEncoder.encode(bootstrapPassword.trim()))
                    .rol("ADMIN_GENERAL")
                    .sedeId(null)
                    .activo(true)
                    .build();
            usuarioRepository.save(admin);
            log.info("Usuario inicial ADMIN_GENERAL creado exitosamente con ID: {}", admin.getId());
        }
    }
}
