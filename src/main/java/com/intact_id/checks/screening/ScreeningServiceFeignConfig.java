package com.intact_id.checks.screening;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/** Not a @Configuration: scoped to ScreeningServiceClient only, so it never replaces the shared Feign timeouts. */
public class ScreeningServiceFeignConfig {

    @Bean
    public Request.Options screeningServiceRequestOptions() {
        // Screening calls OpenSanctions (or yente) upstream, so allow more than a local call
        return new Request.Options(3, TimeUnit.SECONDS, 30, TimeUnit.SECONDS, true);
    }
}
