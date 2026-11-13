package com.intact_id.orchestration.kyb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface KybSettingsRepository extends JpaRepository<KybSettingsEntity, String> {

    Optional<KybSettingsEntity> findByCompanyId(String companyId);

    Optional<KybSettingsEntity> findByCompanyIdIsNull();
}
