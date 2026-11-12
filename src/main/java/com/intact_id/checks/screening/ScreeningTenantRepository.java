package com.intact_id.checks.screening;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningTenantRepository extends JpaRepository<ScreeningTenant, String> {
}
