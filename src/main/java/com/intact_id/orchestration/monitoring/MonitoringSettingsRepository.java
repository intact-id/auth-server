package com.intact_id.orchestration.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface MonitoringSettingsRepository extends JpaRepository<MonitoringSettingsEntity, String> {
    Optional<MonitoringSettingsEntity> findByCompanyId(String companyId);
}
