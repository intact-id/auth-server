package com.intact_id.checks.screening;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyCreated;
import com.intact_id.accounts.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Every company gets its sactions-service tenant (type ENTITY) when it is created, so it can set up risk models at once. */
@Slf4j
@Component
@RequiredArgsConstructor
class CompanyScreeningListener {

    private final ScreeningTenants tenants;
    private final ScreeningTenantRepository links;
    private final CompanyRepository companies;

    @ApplicationModuleListener
    void on(CompanyCreated created) {
        tenants.tenantFor(created.companyId(), created.legalName());
    }

    /** Companies created before this existed; a no-op once every company has its tenant. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    void backfill() {
        for (Company company : companies.findAll()) {
            if (links.existsById(company.getId())) continue;
            try {
                tenants.tenantFor(company.getId(), company.getLegalName());
            } catch (RuntimeException e) {
                log.warn("Could not create screening tenant for company {} yet: {}", company.getId(), e.getMessage());
                return;
            }
        }
    }
}
