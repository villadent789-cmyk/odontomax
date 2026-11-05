package com.odontomax.infrastructure.persistence.repository;

import com.odontomax.infrastructure.persistence.entity.TratamientoItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataTratamientoItemRepository extends JpaRepository<TratamientoItemEntity, UUID> {

    List<TratamientoItemEntity> findByTratamientoId(UUID tratamientoId);
}
