package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kyc.handoff")
public class KycHandoffConfig {

    private Duration codeTtl = Duration.ofMinutes(10);

    private String mobileBaseUrl = "http://localhost:5173/m";

    private int claimMaxAttempts = 20;

    private Duration claimWindow = Duration.ofMinutes(10);
}
