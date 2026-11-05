package com.odontomax.identidad.testsupport;

import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.springframework.context.ApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Usuarios de prueba para tests de otros módulos, que no deben tocar la persistencia de identidad
 * (regla 5 de ArquitecturaCapasTest).
 */
public final class UsuarioTestFixtures {

    private UsuarioTestFixtures() {
    }

    /** Estado de una cuenta guardada en BD (solo lo que necesitan las aserciones de otros módulos). */
    public record UsuarioGuardado(UUID id, String email, String rol, String sedeId, boolean activo) {
        public boolean isActivo() {
            return activo;
        }
    }

    /**
     * Con {@link UsuariosSimuladosWebMvcConfig}: el repositorio simulado devuelve una cuenta activa para el
     * correo, de modo que el filtro JWT autentica los tokens de ese usuario.
     */
    public static void simularUsuarioActivo(ApplicationContext context, UUID id, String email, String nombre,
                                            String rol, String sedeId) {
        when(context.getBean(SpringDataUsuarioRepository.class).findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(UsuarioEntity.builder()
                        .id(id)
                        .email(email)
                        .nombre(nombre)
                        .rol(rol)
                        .sedeId(sedeId)
                        .activo(true)
                        .build()));
    }

    /** Lee una cuenta de la BD real (tests {@code @SpringBootTest}). */
    public static Optional<UsuarioGuardado> buscarPorId(ApplicationContext context, UUID id) {
        return context.getBean(SpringDataUsuarioRepository.class).findById(id)
                .map(u -> new UsuarioGuardado(u.getId(), u.getEmail(), u.getRol(), u.getSedeId(), u.isActivo()));
    }
}
