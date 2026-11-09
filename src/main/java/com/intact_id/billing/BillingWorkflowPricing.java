package com.intact_id.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.billing.client.BillingApiResult;
import com.intact_id.billing.client.BillingServiceClient;
import com.intact_id.billing.client.ResolvedServiceCharge;
import com.intact_id.billing.client.WorkflowPricingRequest;
import com.intact_id.billing.client.WorkflowPricingResult;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.WorkflowPricing;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Workflow prices come from billing-service's service charges (global or per client). */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingWorkflowPricing implements WorkflowPricing {

    private final BillingServiceClient client;
    private final ObjectMapper objectMapper;

    @Override
    public PriceQuote quote(String clientIdentifier, Collection<String> serviceCodes) {
        List<ResolvedServiceCharge> charges = call(() -> client.resolvedServiceCharges(clientIdentifier), "fetch service charges");
        Map<String, ResolvedServiceCharge> byCode = charges.stream()
                .collect(Collectors.toMap(ResolvedServiceCharge::serviceCode, Function.identity(), (a, b) -> b));
        List<String> codes = List.copyOf(new LinkedHashSet<>(serviceCodes));
        List<ServicePrice> priced = codes.stream().filter(byCode::containsKey).map(c -> toPrice(byCode.get(c))).toList();
        List<String> unpriced = codes.stream().filter(c -> !byCode.containsKey(c)).toList();
        BigDecimal total = priced.stream().map(ServicePrice::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = charges.stream().map(ResolvedServiceCharge::currency).findFirst().orElse(null);
        return new PriceQuote(total, currency, priced, unpriced);
    }

    @Override
    public PriceQuote price(String workflowId, String clientIdentifier, Collection<String> serviceCodes) {
        WorkflowPricingResult result = call(() -> client.priceWorkflow(workflowId,
                new WorkflowPricingRequest(clientIdentifier, List.copyOf(serviceCodes))), "price the workflow");
        List<ServicePrice> services = Optional.ofNullable(result.breakdown()).orElse(List.of()).stream().map(this::toPrice).toList();
        return new PriceQuote(result.pricePerCall(), result.currency(), services, List.of());
    }

    private ServicePrice toPrice(ResolvedServiceCharge charge) {
        return new ServicePrice(charge.serviceCode(), charge.name(), charge.price(), charge.source());
    }

    /** billing-service's own message (e.g. "No charge configured in KES for: X") reaches the builder as-is. */
    private <T> T call(java.util.function.Supplier<BillingApiResult<T>> request, String action) {
        try {
            BillingApiResult<T> result = request.get();
            if (result == null || result.data() == null) {
                throw new ApplicationExceptions.IntactBadRequestException("Billing could not " + action);
            }
            return result.data();
        } catch (FeignException.FeignClientException e) {
            throw new ApplicationExceptions.IntactBadRequestException(messageOf(e).orElse("Billing could not " + action));
        } catch (FeignException e) {
            log.error("billing-service failed to {}: {}", action, e.getMessage());
            throw new ApplicationExceptions.IntactInternalServerException("Billing is unavailable; try again shortly");
        }
    }

    private Optional<String> messageOf(FeignException e) {
        try {
            JsonNode body = objectMapper.readTree(e.contentUTF8());
            return Optional.ofNullable(body.path("message").asText(null)).filter(m -> !m.isBlank());
        } catch (Exception parseFailure) {
            return Optional.empty();
        }
    }
}
