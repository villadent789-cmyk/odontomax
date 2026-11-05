package com.odontomax.identidad.infrastructure.persistence.repository;

import com.odontomax.identidad.infrastructure.persistence.entity.Login2faPendienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SpringDataLogin2faPendienteRepository extends JpaRepository<Login2faPendienteEntity, String> {

    /** Suma un intento de forma atómica; 0 filas = el token ya está invalidado o agotó sus intentos. */
    @Modifying
    @Query(value = """
            INSERT INTO seguridad_login_2fa_pendientes (clave, intentos, invalidado, creado_en, expira_en)
            VALUES (:clave, 1, FALSE, :ahora, :expira)
            ON CONFLICT (clave) DO UPDATE SET
                intentos = seguridad_login_2fa_pendientes.intentos + 1,
                expira_en = :expira
            WHERE NOT seguridad_login_2fa_pendientes.invalidado
              AND seguridad_login_2fa_pendientes.intentos < :maximo
            """, nativeQuery = true)
    int sumarIntento(@Param("clave") String clave, @Param("ahora") long ahora,
                     @Param("maximo") int maximo, @Param("expira") Instant expira);

    @Modifying
    @Query("DELETE FROM Login2faPendienteEntity e WHERE e.expiraEn < :ahora")
    int deleteCaducados(@Param("ahora") Instant ahora);
}
