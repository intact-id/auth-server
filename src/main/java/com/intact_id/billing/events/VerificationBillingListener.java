package com.intact_id.billing.events;

import com.intact_id.billing.BillingService;
import com.intact_id.orchestration.VerificationOutcome;
import com.intact_id.orchestration.VerificationSubmitted;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Bills verifications from orchestration's events. Each runs after the publishing transaction
 * commits, in its own transaction; a failure leaves the event incomplete so it is retried.
 */
@Component
@RequiredArgsConstructor
class VerificationBillingListener {

    private final BillingService billingService;

    @ApplicationModuleListener
    void on(VerificationSubmitted submitted) {
        billingService.handleVerificationSubmitted(submitted);
    }

    @ApplicationModuleListener
    void on(VerificationOutcome outcome) {
        billingService.handleVerificationOutcome(outcome);
    }
}
