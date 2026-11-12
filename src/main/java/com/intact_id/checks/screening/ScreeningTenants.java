package com.intact_id.checks.screening;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps a company to its sactions-service tenant, creating the tenant on first use. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningTenants {

    private final ScreeningTenantRepository repository;
    private final ScreeningServiceClient client;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String tenantFor(String companyId, String companyName) {
        String cached = cache.get(companyId);
        if (cached != null) return cached;
        // One creation per company, even when its verifications run in parallel
        synchronized (companyId.intern()) {
            String tenantId = repository.findById(companyId).map(ScreeningTenant::getTenantId)
                    .orElseGet(() -> create(companyId, companyName));
            cache.put(companyId, tenantId);
            return tenantId;
        }
    }

    private String create(String companyId, String companyName) {
        ScreeningApi.Envelope<ScreeningApi.Tenant> created = client.createTenant(new ScreeningApi.TenantCreation(
                companyId, "ENTITY", companyName != null && !companyName.isBlank() ? companyName : companyId,
                "Intact ID company", Map.of("companyId", companyId)));
        if (created == null || created.data() == null || created.data().id() == null) {
            throw new IllegalStateException("screening-service returned no tenant for company " + companyId);
        }
        String tenantId = created.data().id();
        repository.save(new ScreeningTenant(companyId, tenantId, LocalDateTime.now()));
        log.info("Created screening tenant {} for company {}", tenantId, companyId);
        return tenantId;
    }
}
