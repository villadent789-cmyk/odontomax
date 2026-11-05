package com.odontomax.identidad.infrastructure.persistence.adapter;

import com.odontomax.identidad.domain.model.Usuario;
import com.odontomax.identidad.infrastructure.persistence.mapper.UsuarioMapperImpl;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import com.odontomax.identidad.infrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioJpaAdapterTest {

    @Mock
    private SpringDataUsuarioRepository springDataUsuarioRepository;

    private UsuarioJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UsuarioJpaAdapter(springDataUsuarioRepository, new UsuarioMapperImpl());
    }

    @Test
    @DisplayName("findById mapea la entidad completa al modelo de dominio")
    void findByIdMapeaEntidad() {
        UUID id = UUID.randomUUID();
        OffsetDateTime acceso = OffsetDateTime.now();
        when(springDataUsuarioRepository.findById(id)).thenReturn(Optional.of(UsuarioEntity.builder()
                .id(id).nombre("Ana").email("ana@odontomax.pe").passwordHash("h").rol("ADMIN_SEDE")
                .sedeId("sede-1").activo(true).ultimoAcceso(acceso).totpSecret("S").totpActivado(true)
                .totpBackupCodes("A,B").build()));

        Usuario u = adapter.findById(id).orElseThrow();

        assertThat(u.getId()).isEqualTo(id);
        assertThat(u.getNombre()).isEqualTo("Ana");
        assertThat(u.getEmail()).isEqualTo("ana@odontomax.pe");
        assertThat(u.getPasswordHash()).isEqualTo("h");
        assertThat(u.getRol()).isEqualTo("ADMIN_SEDE");
        assertThat(u.getSedeId()).isEqualTo("sede-1");
        assertThat(u.isActivo()).isTrue();
        assertThat(u.getUltimoAcceso()).isEqualTo(acceso);
        assertThat(u.getTotpSecret()).isEqualTo("S");
        assertThat(u.isTotpActivado()).isTrue();
        assertThat(u.getTotpBackupCodes()).isEqualTo("A,B");
    }

    @Test
    @DisplayName("save de una cuenta existente copia el estado sobre la misma entidad sin tocar id ni auth_user_id")
    void saveActualizaEntidadExistente() {
        UUID id = UUID.randomUUID();
        UUID authUserId = UUID.randomUUID();
        UsuarioEntity existente = UsuarioEntity.builder().id(id).authUserId(authUserId).nombre("Ana")
                .email("ana@odontomax.pe").passwordHash("h1").rol("ODONTOLOGO").sedeId("sede-1").activo(true).build();
        when(springDataUsuarioRepository.findById(id)).thenReturn(Optional.of(existente));
        when(springDataUsuarioRepository.save(existente)).thenReturn(existente);

        Usuario guardado = adapter.save(Usuario.builder().id(id).nombre("Ana R.").email("ana@odontomax.pe")
                .passwordHash("h2").rol("ADMIN_SEDE").sedeId("sede-2").activo(false).totpActivado(true)
                .totpSecret("S").totpBackupCodes("A,B").build());

        assertThat(existente.getId()).isEqualTo(id);
        assertThat(existente.getAuthUserId()).isEqualTo(authUserId);
        assertThat(existente.getNombre()).isEqualTo("Ana R.");
        assertThat(existente.getPasswordHash()).isEqualTo("h2");
        assertThat(existente.getRol()).isEqualTo("ADMIN_SEDE");
        assertThat(existente.getSedeId()).isEqualTo("sede-2");
        assertThat(existente.isActivo()).isFalse();
        assertThat(existente.isTotpActivado()).isTrue();
        assertThat(existente.getTotpSecret()).isEqualTo("S");
        assertThat(existente.getTotpBackupCodes()).isEqualTo("A,B");
        assertThat(guardado.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("save de una cuenta nueva persiste una entidad sin id")
    void saveInsertaNueva() {
        when(springDataUsuarioRepository.save(org.mockito.ArgumentMatchers.any(UsuarioEntity.class)))
                .thenAnswer(inv -> {
                    UsuarioEntity e = inv.getArgument(0);
                    assertThat(e.getId()).isNull();
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Usuario guardado = adapter.save(Usuario.builder().nombre("Nuevo").email("nuevo@odontomax.pe")
                .passwordHash("h").rol("RECEPCION").sedeId("sede-1").activo(true).build());

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getEmail()).isEqualTo("nuevo@odontomax.pe");
        org.mockito.Mockito.verify(springDataUsuarioRepository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("eliminar borra la entidad y hace flush inmediato")
    void eliminarBorraYHaceFlush() {
        UUID id = UUID.randomUUID();
        UsuarioEntity existente = UsuarioEntity.builder().id(id).build();
        when(springDataUsuarioRepository.findById(id)).thenReturn(Optional.of(existente));

        adapter.eliminar(id);

        var orden = org.mockito.Mockito.inOrder(springDataUsuarioRepository);
        orden.verify(springDataUsuarioRepository).delete(existente);
        orden.verify(springDataUsuarioRepository).flush();
    }

    @Test
    @DisplayName("findById expone el flag de re-cifrado pendiente de la entidad")
    void findByIdExponeRecifrado() {
        UUID id = UUID.randomUUID();
        UsuarioEntity e = UsuarioEntity.builder().id(id).build();
        e.setReencryptionNeeded(true);
        when(springDataUsuarioRepository.findById(id)).thenReturn(Optional.of(e));

        assertThat(adapter.findById(id).orElseThrow().isRecifradoTotpPendiente()).isTrue();
    }

    @Test
    @DisplayName("recifrarCredencialesTotp re-cifra con la clave activa mediante UPDATE explícito (no con save)")
    void recifrarCredencialesTotpUsaClaveActiva() {
        // Aserciones de cifrado de PasswordAndTotpServiceTest.completarLogin2FA_reencryptionNeeded_persisteConClaveActiva
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter.initKeys(
                java.util.Base64.getEncoder().encodeToString(keyBytes), null);
        UUID userId = UUID.randomUUID();

        adapter.recifrarCredencialesTotp(Usuario.builder().id(userId)
                .totpSecret("JBSWY3DPEHPK3PXP").totpBackupCodes("BACKUP01,BACKUP02").build());

        org.mockito.ArgumentCaptor<String> secretCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> codesCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(springDataUsuarioRepository).actualizarCredencialesTotpCifradas(
                org.mockito.ArgumentMatchers.eq(userId), secretCaptor.capture(), codesCaptor.capture());
        // save() no emitiría UPDATE (texto plano sin cambios), por eso no debe ser el mecanismo usado
        org.mockito.Mockito.verify(springDataUsuarioRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());

        com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter converter =
                new com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter();
        assertThat(secretCaptor.getValue()).startsWith("ENC:GCM:v1:");
        assertThat(codesCaptor.getValue()).startsWith("ENC:GCM:v1:");
        assertThat(converter.convertToEntityAttribute(secretCaptor.getValue())).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(com.odontomax.identidad.infrastructure.persistence.converter.AesGcmEncryptionConverter.wasLastDecryptedWithOldKey()).isFalse();
        assertThat(converter.convertToEntityAttribute(codesCaptor.getValue())).isEqualTo("BACKUP01,BACKUP02");
    }

    @Test
    @DisplayName("findById devuelve vacío si no existe la entidad")
    void findByIdInexistente() {
        UUID id = UUID.randomUUID();
        when(springDataUsuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }
}
