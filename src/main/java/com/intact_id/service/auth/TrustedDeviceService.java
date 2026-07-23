package com.intact_id.service.auth;

import com.intact_id.db.model.user.TrustedDevice;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.TrustedDeviceRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.TrustedDeviceDto;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import com.intact_id.util.security.RequestHeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrustedDeviceService {

    private static final long TRUSTED_DEVICE_TTL_DAYS = 30;

    private final TrustedDeviceRepository trustedDeviceRepository;
    private final UserRepository userRepository;
    private final GeneratorUtils generatorUtils;
    private final RequestHeaderUtil requestHeaderUtil;

    @Transactional
    public String issueTrustedDevice(User user, HttpServletRequest request) {
        byte[] randomBytes = new byte[48];
        new SecureRandom().nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String userAgent = request.getHeader("User-Agent");

        TrustedDevice device = new TrustedDevice();
        device.setUser(user);
        device.setDeviceTokenHash(generatorUtils.hashString(rawToken));
        device.setDeviceLabel(deriveDeviceLabel(userAgent));
        device.setIpAddress(requestHeaderUtil.getClientIp(request));
        device.setUserAgent(userAgent);
        device.setExpiresAt(LocalDateTime.now().plusDays(TRUSTED_DEVICE_TTL_DAYS));
        device.setLastUsedAt(LocalDateTime.now());
        trustedDeviceRepository.save(device);

        return rawToken;
    }

    /**
     * Only ever short-circuits the 2FA step of login — the caller must have already
     * verified the password before consulting this.
     */
    @Transactional
    public boolean isDeviceTrusted(User user, String rawToken) {
        String hash = generatorUtils.hashString(rawToken);
        return trustedDeviceRepository.findByDeviceTokenHash(hash)
                .filter(device -> device.getUser().getId().equals(user.getId()))
                .filter(TrustedDevice::isValid)
                .map(device -> {
                    device.setLastUsedAt(LocalDateTime.now());
                    trustedDeviceRepository.save(device);
                    return true;
                })
                .orElse(false);
    }

    public List<TrustedDeviceDto> listTrustedDevices(String username) {
        User user = findUser(username);
        return trustedDeviceRepository.findByUserAndRevokedFalseOrderByLastUsedAtDesc(user).stream()
                .map(d -> new TrustedDeviceDto(d.getId(), d.getDeviceLabel(), d.getIpAddress(),
                        d.getLastUsedAt(), d.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeTrustedDevice(String username, String deviceId) {
        User user = findUser(username);
        TrustedDevice device = trustedDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Device not found: " + deviceId));

        if (!device.getUser().getId().equals(user.getId())) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Access denied");
        }

        device.revoke();
        trustedDeviceRepository.save(device);
    }

    private String deriveDeviceLabel(String userAgent) {
        if (userAgent == null) {
            return "Unknown device";
        }

        String browser = "Unknown browser";
        if (userAgent.contains("Edg/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("Safari/")) {
            browser = "Safari";
        }

        String os = "Unknown OS";
        if (userAgent.contains("Windows")) {
            os = "Windows";
        } else if (userAgent.contains("Mac OS")) {
            os = "macOS";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            os = "iOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        }

        return browser + " on " + os;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + username));
    }
}
