package com.intact_id.client.billing;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Scoped to BillingServiceClient only (passed via @FeignClient(configuration=...), which gives
 * each Feign client its own child context) — deliberately NOT the shared FeignConfig, whose
 * 120s read timeout is sized for face/OCR inference, not a pre-verification balance check that
 * should fail fast so a slow billing-service doesn't stall every submission.
 */
@Configuration
public class BillingServiceFeignConfig {

    @Bean
    public Request.Options billingServiceRequestOptions() {
        return new Request.Options(2, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }
}
