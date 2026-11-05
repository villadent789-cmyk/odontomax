package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.ComprobanteCorrelativoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataComprobanteCorrelativoRepository extends JpaRepository<ComprobanteCorrelativoEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ComprobanteCorrelativoEntity c WHERE c.serie = :serie")
    Optional<ComprobanteCorrelativoEntity> findBySerieWithLock(@Param("serie") String serie);
}
