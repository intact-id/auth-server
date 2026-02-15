package com.intact_id.util.generator;

import com.intact_id.config.WebhookConfig;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
public class GeneratorUtils {

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom random = new SecureRandom();

    private final SecureRandom secureRandom = new SecureRandom();
    private final WebhookConfig webhookConfig;

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
     * Generate a secure random password
     */
    public String generateRandomPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        // Ensure at least one of each required character type
        password.append(getRandomChar("ABCDEFGHIJKLMNOPQRSTUVWXYZ")); // Uppercase
        password.append(getRandomChar("abcdefghijklmnopqrstuvwxyz")); // Lowercase
        password.append(getRandomChar("0123456789")); // Digit
        password.append(getRandomChar("@$!%*?&")); // Special char

        // Fill the rest randomly
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(getRandomChar(PASSWORD_CHARS));
        }

        // Shuffle the password
        return shuffleString(password.toString());
    }

    private char getRandomChar(String chars) {
        return chars.charAt(random.nextInt(chars.length()));
    }

    private String shuffleString(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    /**
     * Generate DEV API key — issued automatically on company approval.
     * Format: ak_dev_xxxxxxxxxxxxxxxxxxxxxxxx (32 chars after prefix)
     */
    public String generateDevApiKey() {
        return "ak_dev_" + generateSecureRandomString(32);
    }

    /**
     * Generate DEV API secret — issued automatically on company approval.
     * Format: as_dev_xxxxxxxxxxxxxxxxxxxxxxxx
     */
    public String generateDevApiSecret() {
        return "as_dev_" + generateSecureRandomString(32);
    }

    /**
     * Generate PROD (live) API key — generated on demand by the company.
     * Format: ak_live_xxxxxxxxxxxxxxxxxxxxxxxx (32 chars after prefix)
     */
    public String generateApiKey() {
        return "ak_live_" + generateSecureRandomString(32);
    }

    /**
     * Generate PROD (live) API secret — generated on demand by the company.
     * Format: as_live_xxxxxxxxxxxxxxxxxxxxxxxx
     */
    public String generateApiSecret() {
        return "as_live_" + generateSecureRandomString(32);
    }

    /**
     * Generate a webhook signing secret.
     * Format: whsec_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (32 random chars after prefix)
     */
    public String generateWebhookSecret() {
        return "whsec_" + generateSecureRandomString(32);
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
            throw new ApplicationExceptions.IntactResourceNotFoundException("SHA-256 algorithm not found" + e);
        }
    }

    /**
     * Validate plain string against hashed value
     */
    public boolean validateHash(String plain, String hashed) {
        log.info("{} {} from util", plain, hashed);
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

    public String generateWebhookSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(webhookConfig.getSignatureAlgorithm());
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    webhookConfig.getSignatureAlgorithm());
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            log.error("Failed to generate webhook signature", e);
            throw new ApplicationExceptions.IntactInternalServerException("Signature generation failed" + e);
        }
    }
}
