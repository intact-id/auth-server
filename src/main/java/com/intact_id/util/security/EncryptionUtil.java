package com.intact_id.util.security;

import com.intact_id.util.exception.ApplicationExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Component
public class EncryptionUtil {
    public final String ALGORITHM_NAME = "AES";
    public final Charset STANDARD_CHARSETS = StandardCharsets.UTF_8;
    public final String SECRET = "b2039573c5b302ab3b0c1ffc8494c59f615cb323e800c3b9531c5ce250d27c8c";
    byte[] keyBytes = HexFormat.of().parseHex(SECRET);

    SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM_NAME);

    public String encrypt(String input) {
        log.debug("Encrypting input string: {}", input);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(input.getBytes(STANDARD_CHARSETS));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new ApplicationExceptions.IntactBadRequestException("Try again later");
        }
    }

    public String decrypt(String input) {
        log.debug("Trying to decrypt {}", input);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM_NAME);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(input);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, STANDARD_CHARSETS);

        } catch (Exception e) {
            throw new ApplicationExceptions.IntactBadRequestException("Try again later");
        }
    }

}
