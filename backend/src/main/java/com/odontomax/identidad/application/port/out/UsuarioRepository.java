package com.odontomax.identidad.application.port.out;

import com.odontomax.identidad.domain.model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {

    Optional<Usuario> findById(UUID id);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findAll();

    /** Inserta (id nulo) o actualiza la cuenta y devuelve el estado guardado. */
    Usuario save(Usuario usuario);

    /** Borrado físico con flush inmediato: la integridad referencial se comprueba aquí y no en el commit. */
    void eliminar(UUID id);

    /** ¿Aparece el usuario en alguna FK sin ON DELETE CASCADE (historial clínico, caja, pagos...)? */
    boolean tieneRegistrosVinculados(UUID id);

    /**
     * Reescribe las credenciales 2FA cifradas con la clave activa. Necesario tras rotar la clave:
     * el texto plano no cambia y un save normal no emitiría UPDATE.
     */
    void recifrarCredencialesTotp(Usuario usuario);
}
