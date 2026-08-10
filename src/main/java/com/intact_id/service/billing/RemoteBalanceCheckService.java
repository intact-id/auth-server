package com.intact_id.service.billing;

import com.intact_id.client.billing.BalanceCheckReason;
import com.intact_id.client.billing.BalanceCheckRequest;
import com.intact_id.client.billing.BalanceCheckResponse;
import com.intact_id.client.billing.BillingApiResult;
import com.intact_id.client.billing.BillingServiceClient;
import com.intact_id.service.billing.events.KafkaBillingEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gates verification submission against billing-service's balance, replacing the local
 * credit-limit check (BillingService.checkSufficientBalance — deprecated, see there).
 *
 * PROFILE_NOT_FOUND is treated as allowed rather than held: billing-service is still being
 * onboarded company by company, and a company with no profile there yet should behave the same
 * as it does today (no billing gate), not suddenly get blocked. Every other reason
 * (INSUFFICIENT_BALANCE, PROFILE_INACTIVE, PRICING_NOT_CONFIGURED, WALLET_NOT_CONFIGURED) holds,
 * and so does billing-service being unreachable or erroring — fail closed, since those cases
 * can't be told apart from "we don't know if this company can pay."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteBalanceCheckService {

    private final BillingServiceClient billingServiceClient;

    public record HoldDecision(boolean hold, String reason) {
        public static final HoldDecision ALLOW = new HoldDecision(false, null);
    }

    public HoldDecision check(String clientIdentifier) {
        BillingApiResult<BalanceCheckResponse> result;
        try {
            result = billingServiceClient.checkBalance(
                    new BalanceCheckRequest(clientIdentifier, KafkaBillingEventType.KYC_VERIFICATION_CALL.name(), 1));
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
