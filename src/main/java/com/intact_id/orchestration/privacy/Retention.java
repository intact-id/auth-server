package com.intact_id.orchestration.privacy;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies retention to verifications: works out each one's dates from the rules of its data's country, deletes
 * what is due every night, erases on request (never during a legal hold, and before a minimum period only when a
 * super admin overrides it), and records legal holds and the end of customer relationships.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Retention {

    static final String RETENTION_ACTOR = "retention";
    private static final List<GlobalStatuses> FINISHED = List.of(GlobalStatuses.COMPLETED, GlobalStatuses.REJECTED,
            GlobalStatuses.FAILED, GlobalStatuses.CANCELLED);
    private static final int BATCH = 500;
    private static final int MAX_BATCHES = 40;

    private final VerificationRequestRepository verifications;
    private final RetentionRules rules;
    private final DataEraser eraser;
    private final DataErasureRepository erasures;

    /** One kind of data's period for one verification. dueAt null: kept (no period, or waiting for the relationship to end). */
    public record CategoryStatus(RetentionCategory category, Integer days, StartsFrom startsFrom, LocalDateTime dueAt,
                                 LocalDateTime doneAt, Integer minDays, LocalDateTime keepUntil, boolean waitingForRelationshipEnd,
                                 String legalBasis, String source) {
    }

    public record Status(String verificationId, String country, String countryName, boolean finished, boolean legalHold,
                         String legalHoldReason, String legalHoldBy, LocalDateTime legalHoldAt, LocalDateTime relationshipEndedAt,
                         LocalDateTime nextDeletionAt, List<CategoryStatus> categories) {
    }

    // ----- status -----

    public Status status(String companyId, String verificationId) {
        VerificationRequest request = owned(companyId, verificationId);
        return status(request, rules.resolver(request.getCompany().getId()));
    }

    private Status status(VerificationRequest request, RetentionRules.Resolver resolver) {
        String country = country(request);
        LocalDateTime finishedAt = FINISHED.contains(request.getStatus()) ? request.getCompletedAt() != null
                ? request.getCompletedAt() : request.getUpdatedAt() : null;
        List<CategoryStatus> categories = new ArrayList<>();
        for (RetentionCategory category : RetentionCategory.values()) {
            RetentionRules.EffectiveRule rule = resolver.effective(country, category);
            LocalDateTime start = rule.startsFrom() == StartsFrom.RELATIONSHIP_END ? request.getRelationshipEndedAt() : finishedAt;
            boolean waiting = rule.startsFrom() == StartsFrom.RELATIONSHIP_END && request.getRelationshipEndedAt() == null;
            categories.add(new CategoryStatus(category, rule.days(), rule.startsFrom(),
                    start == null || rule.days() == null ? null : start.plusDays(rule.days()),
                    doneAt(request, category), rule.minDays(),
                    start == null || rule.minDays() == null ? null : start.plusDays(rule.minDays()),
                    waiting, rule.legalBasis(), rule.source()));
        }
        LocalDateTime next = categories.stream().filter(c -> c.doneAt() == null && c.dueAt() != null)
                .map(CategoryStatus::dueAt).min(LocalDateTime::compareTo).orElse(null);
        return new Status(request.getId(), country, country == null ? null : Countries.name(country), finishedAt != null,
                request.isLegalHold(), request.getLegalHoldReason(), request.getLegalHoldBy(), request.getLegalHoldAt(),
                request.getRelationshipEndedAt(), request.isLegalHold() ? null : next, categories);
    }

    private static LocalDateTime doneAt(VerificationRequest request, RetentionCategory category) {
        return switch (category) {
            case BIOMETRICS -> request.getBiometricsDeletedAt();
            case DOCUMENTS -> request.getDocumentsDeletedAt();
            case PERSONAL_DATA -> request.getPersonalDataErasedAt();
            case RECORD -> request.getRecordPurgedAt();
        };
    }

    /** The data's country, saved on the verification the first time it is worked out. */
    private String country(VerificationRequest request) {
        if (request.getDataCountry() == null) {
            Countries.ofData("business".equalsIgnoreCase(request.getVerificationType()), request.getCustomerData(),
                    request.getCompany().getCountry()).ifPresent(request::setDataCountry);
        }
        return request.getDataCountry();
    }

    // ----- nightly run -----

    /** Plans new and changed verifications, then deletes what is due. Every night at 02:30 server time by default. */
    @Scheduled(cron = "${privacy.retention-cron:0 30 2 * * *}")
    public void run() {
        int planned = plan();
        int erased = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<VerificationRequest> due = verifications.findRetentionDue(LocalDateTime.now(), PageRequest.of(0, BATCH));
            if (due.isEmpty()) break;
            Map<String, RetentionRules.Resolver> resolvers = new HashMap<>();
            for (VerificationRequest request : due) {
                RetentionRules.Resolver resolver = resolvers.computeIfAbsent(request.getCompany().getId(), rules::resolver);
                try {
                    if (applyDue(request, resolver)) erased++;
                } catch (RuntimeException e) {
                    log.warn("Retention could not erase verification {}: {}", request.getId(), e.getMessage());
                    // Try again tomorrow rather than in this run's next batch
                    request.setNextRetentionAt(LocalDateTime.now().plusDays(1));
                    verifications.save(request);
                }
            }
        }
        if (planned + erased > 0) log.info("Retention: planned {} verifications, erased data from {}", planned, erased);
    }

    /** Works out the next deletion date of every verification that needs it. */
    @Transactional
    public int plan() {
        int planned = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<VerificationRequest> pending = verifications.findUnplanned(FINISHED, PageRequest.of(0, BATCH));
            if (pending.isEmpty()) break;
            Map<String, RetentionRules.Resolver> resolvers = new HashMap<>();
            for (VerificationRequest request : pending) {
                Status status = status(request, resolvers.computeIfAbsent(request.getCompany().getId(), rules::resolver));
                request.setNextRetentionAt(status.nextDeletionAt());
                request.setRetentionPlanned(true);
                verifications.save(request);
                planned++;
            }
            verifications.flush();
        }
        return planned;
    }

    /** Erases the furthest kind of data now due (which includes the ones before it), then replans. */
    private boolean applyDue(VerificationRequest request, RetentionRules.Resolver resolver) {
        Status status = status(request, resolver);
        LocalDateTime now = LocalDateTime.now();
        RetentionCategory furthest = null;
        for (CategoryStatus c : status.categories()) {
            if (c.doneAt() == null && c.dueAt() != null && !c.dueAt().isAfter(now)) furthest = c.category();
        }
        if (furthest == null) {
            request.setNextRetentionAt(status.nextDeletionAt());
            verifications.save(request);
            return false;
        }
        eraser.erase(request, furthest, DataErasure.Trigger.RETENTION, "Retention rule for " + status.countryName(), RETENTION_ACTOR, false);
        Status after = status(request, resolver);
        request.setNextRetentionAt(after.nextDeletionAt());
        request.setRetentionPlanned(true);
        verifications.save(request);
        return true;
    }

    // ----- on request -----

    /**
     * Erases data now, for example when an applicant asks. Refused during a legal hold, and while a minimum period
     * still applies to any kind of data it would erase, unless a super admin overrides it (recorded in the log).
     */
    public DataErasure eraseNow(String companyId, String verificationId, RetentionCategory scope, String reason,
                                String performedBy, boolean override, boolean superAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("Say why the data is erased (e.g. the applicant asked)");
        }
        if (override && !superAdmin) {
            throw new ApplicationExceptions.IntactForbiddenException("Only Intact super admins can erase data before its minimum period");
        }
        RetentionCategory what = scope == null ? RetentionCategory.PERSONAL_DATA : scope;
        VerificationRequest request = owned(companyId, verificationId);
        if (!FINISHED.contains(request.getStatus())) {
            throw new ApplicationExceptions.IntactBadRequestException("Only a finished verification can be erased; decide or cancel it first");
        }
        if (request.isLegalHold()) {
            throw new ApplicationExceptions.IntactBadRequestException("This verification is on legal hold (" + request.getLegalHoldReason()
                    + "); release the hold first");
        }
        if (doneAt(request, what) != null) {
            throw new ApplicationExceptions.IntactBadRequestException("That data was already erased");
        }

        Status status = status(request, rules.resolver(request.getCompany().getId()));
        LocalDateTime now = LocalDateTime.now();
        List<String> blocking = new ArrayList<>();
        for (CategoryStatus c : status.categories()) {
            if (!what.includes(c.category()) || c.doneAt() != null || c.minDays() == null) continue;
            String basis = c.legalBasis() != null ? " under " + c.legalBasis() : "";
            if (c.waitingForRelationshipEnd()) {
                blocking.add(RetentionRules.humanize(c.category()) + " must be kept " + c.minDays()
                        + " days after the customer relationship ends" + basis);
            } else if (c.keepUntil() != null && c.keepUntil().isAfter(now)) {
                blocking.add(RetentionRules.humanize(c.category()) + " must be kept until "
                        + c.keepUntil().format(DateTimeFormatter.ISO_LOCAL_DATE) + basis);
            }
        }
        if (!blocking.isEmpty() && !override) {
            throw new ApplicationExceptions.IntactBadRequestException(String.join("; ", blocking));
        }
        return eraser.erase(request, what, DataErasure.Trigger.MANUAL, reason.trim(), performedBy, !blocking.isEmpty());
    }

    // ----- legal hold and relationship end -----

    @Transactional
    public Status hold(String companyId, String verificationId, String reason, String by) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("Say why the data is held (e.g. a dispute or regulator request)");
        }
        VerificationRequest request = owned(companyId, verificationId);
        request.setLegalHold(true);
        request.setLegalHoldReason(reason.trim());
        request.setLegalHoldBy(by);
        request.setLegalHoldAt(LocalDateTime.now());
        request.setRetentionPlanned(false);
        verifications.save(request);
        log.info("Verification {} put on legal hold by {}", verificationId, by);
        return status(companyId, verificationId);
    }

    @Transactional
    public Status release(String companyId, String verificationId, String by) {
        VerificationRequest request = owned(companyId, verificationId);
        request.setLegalHold(false);
        request.setLegalHoldReason(null);
        request.setLegalHoldBy(null);
        request.setLegalHoldAt(null);
        request.setRetentionPlanned(false);
        verifications.save(request);
        log.info("Legal hold on verification {} released by {}", verificationId, by);
        return status(companyId, verificationId);
    }

    /** The customer relationship ended at {@code endedAt} (now when null); periods counted from it can start. */
    @Transactional
    public Status relationshipEnded(String companyId, String verificationId, LocalDateTime endedAt) {
        VerificationRequest request = owned(companyId, verificationId);
        markEnded(request, endedAt);
        return status(companyId, verificationId);
    }

    /** The same, for every verification the company has under this externalReference (one customer). */
    @Transactional
    public int customerRelationshipEnded(String companyId, String externalReference, LocalDateTime endedAt) {
        List<VerificationRequest> all = verifications.findByCompanyIdAndExternalReference(companyId, externalReference);
        if (all.isEmpty()) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("No verifications with externalReference " + externalReference);
        }
        all.forEach(request -> markEnded(request, endedAt));
        return all.size();
    }

    private void markEnded(VerificationRequest request, LocalDateTime endedAt) {
        LocalDateTime at = endedAt == null ? LocalDateTime.now() : endedAt;
        if (at.isAfter(LocalDateTime.now())) {
            throw new ApplicationExceptions.IntactBadRequestException("The relationship can't end in the future");
        }
        request.setRelationshipEndedAt(at);
        request.setRetentionPlanned(false);
        verifications.save(request);
    }

    public List<Map<String, Object>> holds(String companyId) {
        return verifications.findOnLegalHold(companyId).stream().map(v -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("verificationId", v.getId());
            row.put("externalReference", v.getExternalReference());
            row.put("reason", v.getLegalHoldReason());
            row.put("by", v.getLegalHoldBy());
            row.put("at", v.getLegalHoldAt());
            return row;
        }).toList();
    }

    public List<DataErasure> log(String companyId, int limit) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 500));
        return companyId == null ? erasures.findAllByOrderByCreatedAtDesc(page) : erasures.findByCompanyIdOrderByCreatedAtDesc(companyId, page);
    }

    /** A verification the caller may act on: their company's, or any when companyId is null (super admins). */
    private VerificationRequest owned(String companyId, String verificationId) {
        return verifications.findByIdWithCompany(verificationId)
                .filter(v -> companyId == null || Objects.equals(v.getCompany().getId(), companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Verification not found"));
    }
}
