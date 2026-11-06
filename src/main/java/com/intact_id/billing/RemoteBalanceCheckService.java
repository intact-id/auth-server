package com.intact_id.billing;

import com.intact_id.billing.client.BalanceCheckReason;
import com.intact_id.billing.client.BalanceCheckRequest;
import com.intact_id.billing.client.BalanceCheckResponse;
import com.intact_id.billing.client.BillingApiResult;
import com.intact_id.billing.client.BillingServiceClient;
import com.intact_id.billing.events.KafkaBillingEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteBalanceCheckService {

    private final BillingServiceClient billingServiceClient;

    public record HoldDecision(boolean hold, String reason) {
        public static final HoldDecision ALLOW = new HoldDecision(false, null);
    }

    /**
     * verificationId doubles as the check-balance lock's externalRef — BillingService later
     * publishes the outcome's Kafka event with this same id as eventId, so the real debit it
     * triggers in billing-service releases this exact lock instead of waiting out its TTL.
     */
    public HoldDecision check(String clientIdentifier, String verificationId) {
        BillingApiResult<BalanceCheckResponse> result;
        try {
            result = billingServiceClient.checkBalance(
                    new BalanceCheckRequest(clientIdentifier, KafkaBillingEventType.KYC_VERIFICATION_CALL.name(),
                            true, verificationId, 1));
        } catch (Exception e) {
            log.error("billing-service check-balance call failed for client {} — holding (fail closed): {}",
                    clientIdentifier, e.getMessage(), e);
            return new HoldDecision(true, "billing-service unreachable: " + e.getMessage());
        }

        BalanceCheckResponse balance = result != null ? result.data() : null;
        if (balance == null) {
            log.warn("billing-service check-balance returned no data for client {} — holding (fail closed)", clientIdentifier);
            return new HoldDecision(true, "billing-service returned no data");
        }

        if (balance.allowed()) {
            return HoldDecision.ALLOW;
        }

        if (balance.reason() == BalanceCheckReason.PROFILE_NOT_FOUND) {
            log.debug("billing-service has no profile for client {} yet — treating as allowed", clientIdentifier);
            return HoldDecision.ALLOW;
        }

        String reason = "billing-service: " + balance.reason();
        log.info("billing-service check-balance held client {}: {} (requiredAmount={} currency={})",
                clientIdentifier, reason, balance.requiredAmount(), balance.currency());
        return new HoldDecision(true, reason);
    }
}
