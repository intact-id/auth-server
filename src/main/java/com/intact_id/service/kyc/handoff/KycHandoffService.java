package com.intact_id.service.kyc.handoff;

import com.intact_id.config.KycHandoffConfig;
import com.intact_id.constant.enums.kyc.KycDraftStatus;
import com.intact_id.constant.enums.kyc.KycHandoffStatus;
import com.intact_id.constant.enums.kyc.WidgetChannel;
import com.intact_id.db.model.kyc.KycDraft;
import com.intact_id.db.model.kyc.KycHandoff;
import com.intact_id.db.repository.kyc.KycDraftRepository;
import com.intact_id.db.repository.kyc.KycHandoffRepository;
import com.intact_id.dto.response.kyc.KycDraftResponseDto;
import com.intact_id.dto.response.kyc.KycDraftSessionResponseDto;
import com.intact_id.dto.response.kyc.KycHandoffResponseDto;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.draft.KycDraftService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycHandoffService {

    private static final int CODE_BYTES = 16;
    private static final List<KycHandoffStatus> ACTIVE_STATUSES =
            List.of(KycHandoffStatus.PENDING, KycHandoffStatus.CLAIMED);
    private static final String INVALID_CODE_MESSAGE = "This code is invalid or has expired";

    private final KycHandoffRepository handoffRepository;
    private final KycDraftRepository draftRepository;
    private final KycDraftService draftService;
    private final HandoffClaimRateLimiter rateLimiter;
    private final GeneratorUtils generatorUtils;
    private final KycHandoffConfig config;

    public KycHandoffResponseDto createHandoff(WidgetSessionContext context, String draftId) {
        draftService.requireEmbedded(context);
        draftService.requireEditableDraft(context, draftId);

        // Only one live code per draft; a new QR invalidates the old one and any phone using it
        handoffRepository.revokeAllForDraft(draftId, ACTIVE_STATUSES);

        String code = generatorUtils.generateUrlSafeToken(CODE_BYTES);
        KycHandoff handoff = new KycHandoff();
        handoff.setDraftId(draftId);
        handoff.setCodeHash(generatorUtils.hashString(code));
        handoff.setExpiresAt(LocalDateTime.now().plus(config.getCodeTtl()));
        handoff = handoffRepository.save(handoff);

        log.info("Created handoff {} for KYC draft {}", handoff.getId(), draftId);
        return KycHandoffResponseDto.builder()
                .handoffId(handoff.getId())
                .url(config.getMobileBaseUrl() + "#c=" + code)
                .expiresAt(handoff.getExpiresAt())
                .build();
    }

    public KycDraftResponseDto revokeHandoff(WidgetSessionContext context, String draftId) {
        draftService.requireEmbedded(context);
        KycDraft draft = draftService.requireAccessibleDraft(context, draftId);
        int revoked = handoffRepository.revokeAllForDraft(draftId, ACTIVE_STATUSES);
        log.info("Revoked {} handoff(s) for KYC draft {}", revoked, draftId);
        return draftService.toResponse(draft);
    }

    public KycDraftSessionResponseDto claim(String code, String clientIp, String userAgent) {
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new ApplicationExceptions.IntactTooManyRequestsException("Too many attempts. Try again later.");
        }

        // Same error for unknown, used and expired codes so the endpoint can't be used as an oracle
        KycHandoff handoff = handoffRepository.findByCodeHash(generatorUtils.hashString(code.trim()))
                .filter(KycHandoff::isClaimable)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(INVALID_CODE_MESSAGE));

        KycDraft draft = draftRepository.findById(handoff.getDraftId())
                .filter(d -> !d.isExpired() && d.getStatus() == KycDraftStatus.IN_PROGRESS)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "This verification is no longer available"));

        if (handoffRepository.claim(handoff.getId(), LocalDateTime.now(), truncate(userAgent, 255)) == 0) {
            throw new ApplicationExceptions.IntactResourceNotFoundException(INVALID_CODE_MESSAGE);
        }

        log.info("Handoff {} claimed for KYC draft {}", handoff.getId(), draft.getId());
        return draftService.sessionResponse(draft, WidgetChannel.HANDOFF, handoff.getId());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
