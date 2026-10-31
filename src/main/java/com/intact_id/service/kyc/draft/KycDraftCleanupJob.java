package com.intact_id.service.kyc.draft;

import com.intact_id.config.KycDraftConfig;
import com.intact_id.db.repository.kyc.KycDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycDraftCleanupJob {

    private final KycDraftRepository draftRepository;
    private final KycDraftService draftService;
    private final KycDraftConfig config;

    @Scheduled(fixedDelayString = "${kyc.drafts.cleanup-interval-ms:900000}")
    public void purgeExpiredDrafts() {
        List<String> expiredIds = draftRepository.findExpiredIds(
                LocalDateTime.now(), PageRequest.of(0, config.getCleanupBatchSize()));
        if (expiredIds.isEmpty()) {
            return;
        }

        int purged = 0;
        for (String draftId : expiredIds) {
            try {
                draftService.deleteDraft(draftId);
                purged++;
            } catch (Exception e) {
                log.warn("Failed to purge expired KYC draft {}", draftId, e);
            }
        }
        log.info("Purged {} expired KYC draft(s)", purged);
    }
}
