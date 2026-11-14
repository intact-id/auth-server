package com.intact_id.orchestration.privacy;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.notifications.WebhookService;
import com.intact_id.orchestration.VerificationDocument;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.kyb.VerificationParty;
import com.intact_id.orchestration.kyb.VerificationPartyRepository;
import com.intact_id.orchestration.model.VerificationDocumentRepository;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.model.VerificationStep;
import com.intact_id.orchestration.model.VerificationStepRepository;
import com.intact_id.orchestration.verification.VerificationDocumentFiles;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deletes applicants' data: documents (uploaded files, selfies, liveness frames), and personal details (names, ID
 * numbers, contact, OCR results, directors' and owners' details, webhook payloads). The decision, the checks'
 * outcomes and scores stay, so a verification remains auditable. Each erasure is recorded in data_erasures.
 * Runs on request, and daily for each company's retention rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataEraser {

    static final String RETENTION_ACTOR = "retention";
    private static final List<GlobalStatuses> FINISHED = List.of(GlobalStatuses.COMPLETED, GlobalStatuses.REJECTED,
            GlobalStatuses.FAILED, GlobalStatuses.CANCELLED);
    private static final int BATCH = 200;
    // What a check's result keeps after erasure: outcomes and scores, no names, numbers or dates
    private static final Set<String> RESULT_KEYS_KEPT = Set.of("verdict", "hits", "top_score", "topScore", "pep", "adverse_media",
            "level", "score", "complete", "override_applied", "passed", "liveness_score", "match_score", "verified",
            "document_type", "overall_confidence", "document_expired", "name_match", "id_number_match", "dob_match",
            "reason", "registered", "status", "mode", "required", "failed", "review", "source");
    // Metadata the platform itself uses; anything else (the merchant's custom fields) is erased
    private static final Set<String> METADATA_KEPT = Set.of("checksRun", "ownerCheck", "autoInvite", "personWorkflow", "resumed");

    private final VerificationRequestRepository verifications;
    private final VerificationDocumentRepository documents;
    private final VerificationDocumentFiles files;
    private final VerificationStepRepository steps;
    private final VerificationPartyRepository parties;
    private final DataErasureRepository erasures;
    private final RetentionSettingsService retention;
    private final CompanyRepository companies;
    private final WebhookService webhooks;
    private final TransactionTemplate transactions;

    /** Erases a finished verification's documents and personal details now, for an applicant's request. */
    public DataErasure eraseNow(String companyId, String verificationId, String reason, String performedBy) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("Say why the data is erased (e.g. the applicant asked)");
        }
        VerificationRequest request = verifications.findByIdWithCompany(verificationId)
                .filter(v -> companyId == null || v.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Verification not found"));
        if (!FINISHED.contains(request.getStatus())) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Only a finished verification can be erased; decide or cancel it first");
        }
        if (request.getPersonalDataErasedAt() != null) {
            throw new ApplicationExceptions.IntactBadRequestException("This verification's personal data was already erased");
        }
        return erase(request, DataErasure.Scope.PERSONAL_DATA, DataErasure.Trigger.MANUAL, reason.trim(), performedBy);
    }

    /** Each company's retention rule, once a day (02:30 server time). */
    @Scheduled(cron = "${privacy.retention-cron:0 30 2 * * *}")
    public void applyRetention() {
        for (Company company : companies.findAll()) {
            RetentionSettings rule = retention.effective(company.getId());
            int documentsDone = 0;
            int personalDone = 0;
            if (rule.personalDataDays() != null) {
                for (VerificationRequest v : verifications.findPersonalDataDue(company.getId(), FINISHED,
                        LocalDateTime.now().minusDays(rule.personalDataDays()), PageRequest.of(0, BATCH))) {
                    personalDone += tryErase(v, DataErasure.Scope.PERSONAL_DATA);
                }
            }
            if (rule.documentsDays() != null) {
                for (VerificationRequest v : verifications.findDocumentsDue(company.getId(), FINISHED,
                        LocalDateTime.now().minusDays(rule.documentsDays()), PageRequest.of(0, BATCH))) {
                    documentsDone += tryErase(v, DataErasure.Scope.DOCUMENTS);
                }
            }
            if (documentsDone + personalDone > 0) {
                log.info("Retention for {}: documents deleted from {} verifications, personal data erased from {}",
                        company.getId(), documentsDone, personalDone);
            }
        }
    }

    public List<DataErasure> log(String companyId, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 500));
        return companyId == null ? erasures.findAllByOrderByCreatedAtDesc(page) : erasures.findByCompanyIdOrderByCreatedAtDesc(companyId, page);
    }

    private int tryErase(VerificationRequest request, DataErasure.Scope scope) {
        try {
            erase(request, scope, DataErasure.Trigger.RETENTION, "Retention rule", RETENTION_ACTOR);
            return 1;
        } catch (RuntimeException e) {
            log.warn("Retention could not erase verification {}: {}", request.getId(), e.getMessage());
            return 0;
        }
    }

    private DataErasure erase(VerificationRequest request, DataErasure.Scope scope, DataErasure.Trigger trigger,
                              String reason, String performedBy) {
        // Files first: if a file can't be deleted, nothing is marked as erased and the next run tries again
        List<VerificationDocument> docs = documents.findByVerificationRequestId(request.getId());
        docs.forEach(files::delete);

        return transactions.execute(status -> {
            documents.deleteAll(docs);
            LocalDateTime now = LocalDateTime.now();
            if (request.getDocumentsDeletedAt() == null) request.setDocumentsDeletedAt(now);
            if (scope == DataErasure.Scope.PERSONAL_DATA) {
                erasePersonalDetails(request);
                request.setPersonalDataErasedAt(now);
            }
            verifications.save(request);

            DataErasure erasure = new DataErasure();
            erasure.setVerificationId(request.getId());
            erasure.setCompanyId(request.getCompany().getId());
            erasure.setScope(scope);
            erasure.setTrigger(trigger);
            erasure.setReason(reason);
            erasure.setPerformedBy(performedBy);
            erasure.setDocumentsDeleted(docs.size());
            log.info("Erased {} of verification {} ({}, {} documents) by {}", scope, request.getId(), trigger, docs.size(), performedBy);
            return erasures.save(erasure);
        });
    }

    private void erasePersonalDetails(VerificationRequest request) {
        boolean business = "business".equalsIgnoreCase(request.getVerificationType());
        if (!business) {
            // A business's own registration details aren't personal data; a person's details are
            request.setCustomerData(new HashMap<>());
        }
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> {
                if (METADATA_KEPT.contains(key)) metadata.put(key, value);
            });
        }
        request.setMetadata(metadata);

        for (VerificationStep step : steps.findByVerificationRequestIdOrderByCreatedAtAsc(request.getId())) {
            Map<String, Object> kept = new HashMap<>();
            if (step.getResult() != null) {
                step.getResult().forEach((key, value) -> {
                    if (RESULT_KEYS_KEPT.contains(key) && !(value instanceof Map) && !(value instanceof List)) kept.put(key, value);
                });
            }
            step.setResult(kept);
            step.setErrorMessage(null);
            steps.save(step);
        }

        for (VerificationParty party : parties.findByVerificationIdOrderByCreatedAtAsc(request.getId())) {
            party.setFirstName("Erased");
            party.setLastName("person");
            party.setDateOfBirth(null);
            party.setNationality(null);
            party.setEmail(null);
            party.setPhone(null);
            party.setScreening(null);
            party.setInviteTokenHash(null);
            party.setInviteExpiresAt(null);
            parties.save(party);
        }

        webhooks.redactDeliveries(request.getId());
    }
}
