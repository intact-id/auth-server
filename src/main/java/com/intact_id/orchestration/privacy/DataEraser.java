package com.intact_id.orchestration.privacy;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erases one kind of a verification's data (and every kind before it, see RetentionCategory), and records it in
 * data_erasures. Files go first: if one can't be deleted, nothing is marked and the next attempt tries again.
 * Whether an erasure is allowed (holds, minimum periods) is decided by Retention, not here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DataEraser {

    // What a check's result keeps once personal details are erased: outcomes and scores only
    private static final Set<String> RESULT_KEYS_KEPT = Set.of("verdict", "hits", "top_score", "topScore", "pep", "adverse_media",
            "level", "score", "complete", "override_applied", "passed", "liveness_score", "match_score", "verified",
            "document_type", "overall_confidence", "document_expired", "name_match", "id_number_match", "dob_match",
            "reason", "registered", "status", "mode", "required", "failed", "review", "source");
    // Metadata the platform itself uses; the merchant's custom fields are personal-data candidates and go
    private static final Set<String> METADATA_KEPT = Set.of("checksRun", "ownerCheck", "autoInvite", "personWorkflow", "resumed");

    private final VerificationRequestRepository verifications;
    private final VerificationDocumentRepository documents;
    private final VerificationDocumentFiles files;
    private final VerificationStepRepository steps;
    private final VerificationPartyRepository parties;
    private final DataErasureRepository erasures;
    private final WebhookService webhooks;
    private final TransactionTemplate transactions;

    DataErasure erase(VerificationRequest request, RetentionCategory scope, DataErasure.Trigger trigger, String reason,
                      String performedBy, boolean overrodeMinimum) {
        List<VerificationDocument> docs = documents.findByVerificationRequestId(request.getId()).stream()
                .filter(d -> scope.includes(RetentionCategory.DOCUMENTS) || isBiometric(d))
                .toList();
        docs.forEach(files::delete);

        return transactions.execute(status -> {
            documents.deleteAll(docs);
            LocalDateTime now = LocalDateTime.now();
            if (request.getBiometricsDeletedAt() == null) request.setBiometricsDeletedAt(now);
            if (scope.includes(RetentionCategory.DOCUMENTS) && request.getDocumentsDeletedAt() == null) request.setDocumentsDeletedAt(now);
            if (scope.includes(RetentionCategory.PERSONAL_DATA) && request.getPersonalDataErasedAt() == null) {
                erasePersonalDetails(request);
                request.setPersonalDataErasedAt(now);
            }
            if (scope == RetentionCategory.RECORD) {
                purgeRecord(request);
                request.setRecordPurgedAt(now);
            }
            request.setRetentionPlanned(false);
            verifications.save(request);

            DataErasure erasure = new DataErasure();
            erasure.setVerificationId(request.getId());
            erasure.setCompanyId(request.getCompany().getId());
            erasure.setScope(scope);
            erasure.setTrigger(trigger);
            erasure.setReason(reason);
            erasure.setPerformedBy(performedBy);
            erasure.setDocumentsDeleted(docs.size());
            erasure.setOverrodeMinimum(overrodeMinimum);
            log.info("Erased {} of verification {} ({}, {} files) by {}{}", scope, request.getId(), trigger, docs.size(),
                    performedBy, overrodeMinimum ? ", before its minimum period" : "");
            return erasures.save(erasure);
        });
    }

    private static boolean isBiometric(VerificationDocument document) {
        String type = document.getDocumentType();
        return "selfie".equals(type) || type.startsWith("liveness_");
    }

    private void erasePersonalDetails(VerificationRequest request) {
        if (!"business".equalsIgnoreCase(request.getVerificationType())) {
            // A business's registration details aren't personal data; a person's details are
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

    /** The end of the record: only a bare row (id, company, status, dates) remains, for the erasure log. */
    private void purgeRecord(VerificationRequest request) {
        request.setCustomerData(new HashMap<>());
        request.setMetadata(new HashMap<>());
        request.setErrorDetails(null);
        request.setReviewReason(null);
        request.setReviewNotes(null);
        steps.deleteAll(steps.findByVerificationRequestIdOrderByCreatedAtAsc(request.getId()));
        parties.deleteAll(parties.findByVerificationIdOrderByCreatedAtAsc(request.getId()));
        webhooks.deleteDeliveries(request.getId());
    }
}
