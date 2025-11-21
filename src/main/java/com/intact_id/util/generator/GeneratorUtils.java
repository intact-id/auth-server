package com.intact_id.util.generator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
/**
 * util class for all parameter / value generation
 */
public class GeneratorUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a human-readable company/customer code
     * Format: COMP-<6-digit random>-ID
     */
    public String generateCompanyIdentifierCode() {
        int number = 100000 + RANDOM.nextInt(900000); // 6-digit random number
        return String.format("COMP-%d-ID", number);
    }

    public String generateApplicationNumber() {
        int year = LocalDateTime.now().getYear();
        String shortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("APP-%d-%s", year, shortId);
    }
}
