package com.intact_id.orchestration.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RetentionSettingsRepository extends JpaRepository<RetentionSettingsEntity, String> {

    Optional<RetentionSettingsEntity> findByCompanyId(String companyId);

    Optional<RetentionSettingsEntity> findByCompanyIdIsNull();
}
