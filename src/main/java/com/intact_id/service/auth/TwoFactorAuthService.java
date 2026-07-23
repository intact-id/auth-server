package com.intact_id.service.auth;

import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.Enable2FaResponseDto;
import com.intact_id.dto.auth.UserCreateDto;
import com.intact_id.dto.auth.UserResponseDto;
import com.intact_id.security.totp.TotpService;
import com.intact_id.service.jwt.JwtService;
import com.intact_id.service.user.UserService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.security.RequestHeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.http.HttpRequest;
import java.util.HashMap;

@Service
@AllArgsConstructor
public class TwoFactorAuthService {
    private final TotpService totpService;
    private final UserService userService;
    private final JwtService jwtService;
    private final RequestHeaderUtil requestHeaderUtil;
    private final UserRepository userRepository;

    public Enable2FaResponseDto enabled2Fa(HttpServletRequest request) {


        var userResponseDto = getUserFromAuthentication(request);
        var user = userService.toUser(userResponseDto);

        user.enable2FA();
        user.setGoogleAuthenticatorKey(totpService.generateEncryptedTotpSecretKey());
        var userCreationDto = new UserCreateDto();
        userCreationDto.setIs2FaEnabled(user.getIs2faEnabled());
        userCreationDto.setGoogleAuthenticatorKey(user.getGoogleAuthenticatorKey());
        userService.updateUser(userResponseDto.getId(), userCreationDto);

        var qrCOdeUrl = getEncodedQrCodeUrl(user.getUsername(), user.getGoogleAuthenticatorKey());

        return new Enable2FaResponseDto(true, qrCOdeUrl, null);
    }

    public Enable2FaResponseDto disabled2Fa(HttpServletRequest request) {
        var userResponseDto = getUserFromAuthentication(request);
        var user = userService.toUser(userResponseDto);
        user.disable2FA();
        user.setGoogleAuthenticatorKey(null);

        var userCreationDto = new UserCreateDto();
        userCreationDto.setIs2FaEnabled(user.getIs2faEnabled());
        userCreationDto.setGoogleAuthenticatorKey(user.getGoogleAuthenticatorKey());
        userService.updateUser(userResponseDto.getId(), userCreationDto);

        return new Enable2FaResponseDto(false, null, null);

    }

    public Boolean validateTotpVerificationCode(Integer verificationCode, HttpServletRequest request) {

        var userResponseDto = getUserFromAuthentication(request);
        return totpService.validateTotpSecretKey(userResponseDto.getGoogleAuthenticationKey(), verificationCode);

    }


    private String getEncodedQrCodeUrl(String userName, String encodedGoogleAuthenticatorKey) {
        return totpService.getEncodedQrCodeUrl(userName, encodedGoogleAuthenticatorKey);
    }


    private UserResponseDto getUserFromAuthentication(HttpServletRequest request) {
        var authHeader = requestHeaderUtil.getHeader(request, "Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Authorisation missing");
        }
        var token = authHeader.substring(7);
        var userName = jwtService.extractUsername(token);

        return userService.getUserByUsername(userName);
    }
}
