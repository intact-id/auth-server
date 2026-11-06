package com.intact_id.notifications.webhook;

import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Signs webhook payloads so receivers can verify they came from us. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSigner {

    private final WebhookConfig webhookConfig;

    public String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(webhookConfig.getSignatureAlgorithm());
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), webhookConfig.getSignatureAlgorithm()));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Failed to generate webhook signature", e);
            throw new ApplicationExceptions.IntactInternalServerException("Signature generation failed" + e);
        }
    }
}
