package com.intact_id.util.generator;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Component
public class GeneratorUtils {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate application number
     * Format: APP-YYYY-XXXXXXXX
     */
    public String generateApplicationNumber() {
        int year = LocalDateTime.now().getYear();
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("APP-%d-%s", year, randomPart);
    }

    /**
     * Generate API key
     * Format: ak_live_xxxxxxxxxxxxxxxxxxxxxxxx (32 chars after prefix)
     */
    public String generateApiKey() {
        String randomPart = generateSecureRandomString(32);
        return "ak_live_" + randomPart;
    }


    public String generateApiSecret() {
        String randomPart = generateSecureRandomString(32);
        return "as_live_" + randomPart;
    }

    /**
     * Hash string using SHA-256
     */
    public String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Validate plain string against hashed value
     */
    public boolean validateHash(String plain, String hashed) {
        String hashedPlain = hashString(plain);
        return hashedPlain.equals(hashed);
    }

    /**
     * Generate customer ID
     * Format: cust_comp_xxxxxxxxxxxx (12 chars after prefix)
     */
    public String generateCustomerId() {
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "cust_comp_" + randomPart;
    }

    /**
     * Generate secure random string (alphanumeric)
     */
    private String generateSecureRandomString(int length) {
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes)
                .substring(0, length)
                .toLowerCase();
    }
}
