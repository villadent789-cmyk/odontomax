package com.odontomax.identidad.infrastructure.persistence.repository;

import com.odontomax.identidad.infrastructure.persistence.entity.LoginIntentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SpringDataLoginIntentoRepository extends JpaRepository<LoginIntentoEntity, String> {

    /** Suma un intento de forma atómica (sin carrera entre peticiones); 0 filas = la clave ya está en el máximo de su ventana. */
    @Modifying
    @Query(value = """
            INSERT INTO seguridad_login_intentos (clave, intentos, primer_intento, expira_en)
            VALUES (:clave, 1, :ahora, :expira)
            ON CONFLICT (clave) DO UPDATE SET
                intentos = CASE WHEN :ahora - seguridad_login_intentos.primer_intento > :ventana
                                THEN 1 ELSE seguridad_login_intentos.intentos + 1 END,
                primer_intento = CASE WHEN :ahora - seguridad_login_intentos.primer_intento > :ventana
                                      THEN :ahora ELSE seguridad_login_intentos.primer_intento END,
                expira_en = :expira
            WHERE :ahora - seguridad_login_intentos.primer_intento > :ventana
               OR seguridad_login_intentos.intentos < :maximo
            """, nativeQuery = true)
    int sumarIntento(@Param("clave") String clave, @Param("ahora") long ahora, @Param("ventana") long ventana,
                     @Param("maximo") int maximo, @Param("expira") Instant expira);

    @Modifying
    @Query("DELETE FROM LoginIntentoEntity e WHERE e.expiraEn < :ahora")
    int deleteCaducados(@Param("ahora") Instant ahora);
}
