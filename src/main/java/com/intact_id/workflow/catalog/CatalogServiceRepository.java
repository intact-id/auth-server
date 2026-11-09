package com.intact_id.workflow.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CatalogServiceRepository extends JpaRepository<CatalogServiceEntity, String> {

    Optional<CatalogServiceEntity> findByCode(String code);

    boolean existsByCode(String code);
}
