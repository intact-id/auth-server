package com.intact_id.billing.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Timeouts for {@link BillingAccountClient} only. Deliberately not a @Configuration: if component scanning
 * picked it up, its options would apply to every Feign client instead of just this one.
 */
public class BillingAccountFeignConfig {

    @Bean
    public Request.Options billingAccountRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 20, TimeUnit.SECONDS, true);
    }
}
