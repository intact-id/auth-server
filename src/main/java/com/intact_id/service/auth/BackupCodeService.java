package com.intact_id.service.auth;

import com.intact_id.db.model.user.BackupCode;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.BackupCodeRepository;
import com.intact_id.dto.auth.BackupCodesResponseDto;
import com.intact_id.service.user.UserService;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BackupCodeService {

    private static final int CODE_COUNT = 10;
    // Excludes ambiguous characters (0/O, 1/I) so codes are easy to type back in correctly.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final BackupCodeRepository backupCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    @Transactional
    public BackupCodesResponseDto generateBackupCodes() {
        // Read straight off the SecurityContext (already populated by JwtAuthenticationFilter)
        // instead of injecting AuthenticationService here — that service already depends on
        // this one (to verify backup codes during login), so injecting it back would form a
        // circular bean dependency.
        User user = SecurityContextUtil.getAuthenticatedUser();
        backupCodeRepository.deleteByUser(user);

        SecureRandom random = new SecureRandom();
        List<String> plainCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode(random);
            plainCodes.add(code);

            BackupCode backupCode = new BackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(passwordEncoder.encode(code));
            backupCodeRepository.save(backupCode);
        }

        return new BackupCodesResponseDto(plainCodes, CODE_COUNT);
    }

    public int getRemainingBackupCodeCount(String username) {
        User user = findUser(username);
        return (int) backupCodeRepository.countByUserAndUsedAtIsNull(user);
    }

    /**
     * Checks the raw code against the user's unused backup codes and marks the match consumed.
     * Single-use: a consumed code can never be reused.
     */
    @Transactional
    public boolean verifyAndConsume(User user, String rawCode) {
        String normalized = rawCode.trim().toUpperCase().replace(" ", "");

        for (BackupCode candidate : backupCodeRepository.findByUserAndUsedAtIsNull(user)) {
            if (passwordEncoder.matches(normalized, candidate.getCodeHash())) {
                candidate.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(candidate);
                return true;
            }
        }
        return false;
    }

    private String generateCode(SecureRandom random) {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private User findUser(String username) {
        var res = userService.getUserByUsername(username);
       return userService.toUser(res);

    }
}
