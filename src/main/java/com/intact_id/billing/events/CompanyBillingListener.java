package com.intact_id.billing.events;

import com.intact_id.accounts.CompanyCreated;
import com.intact_id.billing.account.BillingAccounts;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Registers every new company with billing-service; its billing terms are set from the dashboard. */
@Component
@RequiredArgsConstructor
class CompanyBillingListener {

    private final BillingAccounts billingAccounts;

    @ApplicationModuleListener
    void on(CompanyCreated created) {
        billingAccounts.register(created.companyId());
    }
}
