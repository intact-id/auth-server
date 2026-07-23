package com.intact_id.security.totp;

import com.intact_id.util.security.EncryptionUtil;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Service
@AllArgsConstructor
public class TotpService {
    private final EncryptionUtil encryptionUtil;
    private static final String ISSUER = "INTACT_IO";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public String generateTotpSecretKey() {
        var googleAuthenticator = new GoogleAuthenticator();
        final GoogleAuthenticatorKey googleAuthenticatorKey = googleAuthenticator.createCredentials();
        return googleAuthenticatorKey.getKey();
    }

    public String generateEncryptedTotpSecretKey() {
        return encryptionUtil.encrypt(generateTotpSecretKey());
    }

    public String getEncodedQrCodeUrl(String userName, String encodedGoogleAuthenticatorKey) {


        String BASE_QR_URL = "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30";
        return String.format(BASE_QR_URL,
                URLEncoder.encode(ISSUER, CHARSET),
                URLEncoder.encode(userName, CHARSET),
                URLEncoder.encode(decodeEncodedGoogleAuthenticatorKey(encodedGoogleAuthenticatorKey), CHARSET),
                URLEncoder.encode(ISSUER, CHARSET)
        );
    }

    public Boolean validateTotpSecretKey(String encodedGoogleAuthenticatorKey, Integer verificationCode) {

        GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator(
                new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setWindowSize(1)
                        .build()
        );
        return googleAuthenticator.authorize(decodeEncodedGoogleAuthenticatorKey(encodedGoogleAuthenticatorKey), verificationCode);
    }

    private String decodeEncodedGoogleAuthenticatorKey(String encodedGoogleAuthenticatorKey) {
        return encryptionUtil.decrypt(encodedGoogleAuthenticatorKey);
    }
}
