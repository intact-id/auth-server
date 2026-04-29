package com.intact_id.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    Request.Options requestOptions() {
        // Face verification and OCR inference can take up to 90s on cold start
        return new Request.Options(10, TimeUnit.SECONDS, 120, TimeUnit.SECONDS, true);
    }
}
