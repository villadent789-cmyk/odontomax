package com.odontomax.identidad.infrastructure.persistence.adapter;

import com.odontomax.identidad.application.port.out.UsuarioRepository;
import com.odontomax.identidad.domain.model.Usuario;
import com.odontomax.identidad.infrastructure.persistence.mapper.UsuarioMapper;
import com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioJpaAdapter implements UsuarioRepository {

    private final SpringDataUsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    // Las claves del convertidor son estáticas; la instancia solo expone el cifrado con la clave activa.
    private final AesGcmEncryptionConverter totpConverter = new AesGcmEncryptionConverter();

    @Override
    public Optional<Usuario> findById(UUID id) {
        return usuarioRepository.findById(id).map(usuarioMapper::toDomain);
    }

    @Override
    public Optional<Usuario> findByEmailIgnoreCase(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email).map(usuarioMapper::toDomain);
    }

    @Override
    public List<Usuario> findAll() {
        return usuarioRepository.findAll().stream().map(usuarioMapper::toDomain).toList();
    }

    @Override
    public Usuario save(Usuario usuario) {
        // Dentro de la transacción del caso de uso, findById devuelve la entidad ya gestionada (sin otra consulta)
        UsuarioEntity entity = usuario.getId() != null
                ? usuarioRepository.findById(usuario.getId()).orElseGet(UsuarioEntity::new)
                : new UsuarioEntity();
        usuarioMapper.copiarEstado(usuario, entity);
        return usuarioMapper.toDomain(usuarioRepository.save(entity));
    }

    @Override
    public void eliminar(UUID id) {
        usuarioRepository.findById(id).ifPresent(usuarioRepository::delete);
        usuarioRepository.flush();
    }

    @Override
    public boolean tieneRegistrosVinculados(UUID id) {
        return usuarioRepository.tieneRegistrosVinculados(id);
    }

    @Override
    public void recifrarCredencialesTotp(Usuario usuario) {
        usuarioRepository.actualizarCredencialesTotpCifradas(
                usuario.getId(),
                totpConverter.convertToDatabaseColumn(usuario.getTotpSecret()),
                totpConverter.convertToDatabaseColumn(usuario.getTotpBackupCodes())
        );
    }
}
