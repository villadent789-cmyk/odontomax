package com.odontomax.identidad.infrastructure.persistence.mapper;

import com.odontomax.identidad.domain.model.Usuario;
import com.odontomax.identidad.infrastructure.persistence.entity.UsuarioEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioMapper {

    @Mapping(target = "recifradoTotpPendiente", source = "reencryptionNeeded")
    Usuario toDomain(UsuarioEntity entity);

    /**
     * Copia el estado editable del dominio sobre la entidad. No toca id, auth_user_id,
     * columnas de solo lectura (creado_en, actualizado_en) ni el flag transitorio de re-cifrado.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "rol", source = "rol")
    @Mapping(target = "sedeId", source = "sedeId")
    @Mapping(target = "activo", source = "activo")
    @Mapping(target = "ultimoAcceso", source = "ultimoAcceso")
    @Mapping(target = "totpSecret", source = "totpSecret")
    @Mapping(target = "totpActivado", source = "totpActivado")
    @Mapping(target = "totpBackupCodes", source = "totpBackupCodes")
    @Mapping(target = "tokenVersion", source = "tokenVersion")
    void copiarEstado(Usuario usuario, @MappingTarget UsuarioEntity entity);
}
