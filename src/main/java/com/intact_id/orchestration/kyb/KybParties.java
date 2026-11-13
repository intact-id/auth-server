package com.intact_id.orchestration.kyb;

import com.intact_id.checks.RiskScreening;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.notifications.EmailService;
import com.intact_id.orchestration.VerificationOutcome;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.verification.ExternalKycServiceClient.CheckResult;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The directors and owners behind a business verification: who must be checked (from the KYB settings at
 * submission), screening them or linking their own identity verifications, and the combined result the
 * OWNER_VERIFICATION workflow step reports. With FULL_KYC the business waits until every required person's
 * verification has a final result, then goes back into processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KybParties {

    public static final String OWNER_CHECK = "ownerCheck";
    public static final String WAITING = "waitingFor";
    public static final String WAITING_FOR_PEOPLE = "people";
    /** externalReference of a person's verification started from an invite link */
    public static final String PARTY_REFERENCE_PREFIX = "kyb-party:";
    static final int INVITE_DAYS = 7;

    private final VerificationPartyRepository parties;
    private final VerificationRequestRepository verifications;
    private final KybSettingsService settings;
    private final RiskScreening riskScreening;
    private final GeneratorUtils generatorUtils;
    private final EmailService emailService;

    @Value("${kyb.invite-url:https://widget.intact-io.com/invite}")
    private String inviteUrl;

    /** Saves the people with a new business verification and records how they are checked. Runs in its transaction. */
    public void attach(VerificationRequest business, List<PartyInput> people) {
        KybSettings rules = settings.effective(business.getCompany().getId());
        business.getMetadata().put(OWNER_CHECK, rules.ownerCheck().name());
        for (PartyInput person : people == null ? List.<PartyInput>of() : people) {
            if (blank(person.firstName()) || blank(person.lastName())) {
                throw new ApplicationExceptions.IntactBadRequestException("Each director or owner needs a first and last name");
            }
            if (!person.director() && person.ownershipPercent() == null) {
                throw new ApplicationExceptions.IntactBadRequestException(
                        person.firstName() + " " + person.lastName() + " is neither a director nor an owner (ownershipPercent)");
            }
            VerificationParty party = new VerificationParty();
            party.setVerificationId(business.getId());
            party.setFirstName(person.firstName().trim());
            party.setLastName(person.lastName().trim());
            party.setDateOfBirth(person.dateOfBirth());
            party.setNationality(person.nationality());
            party.setEmail(person.email());
            party.setPhone(person.phone());
            party.setDirector(person.director());
            party.setOwnershipPercent(person.ownershipPercent());
            party.setMustVerify(rules.mustVerify(person.director(), person.ownershipPercent()));
            party.setStatus(party.isMustVerify() ? PartyStatus.PENDING : PartyStatus.NOT_REQUIRED);
            parties.save(party);
        }
    }

    /**
     * Links a person's own identity verification to their place on a business verification. partyId comes from
     * the API, or from an invite session's externalReference. Runs in the new verification's transaction.
     */
    public void linkPerson(String partyId, VerificationRequest person) {
        VerificationParty party = parties.findById(partyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Unknown director or owner: " + partyId));
        VerificationRequest business = verifications.findById(party.getVerificationId())
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Business verification not found"));
        if (!business.getCompany().getId().equals(person.getCompany().getId())) {
            throw new ApplicationExceptions.IntactBadRequestException("Unknown director or owner: " + partyId);
        }
        if (party.getStatus().isFinal()) {
            throw new ApplicationExceptions.IntactBadRequestException(party.fullName() + " already has a result");
        }
        party.setPersonVerificationId(person.getId());
        party.setStatus(PartyStatus.VERIFYING);
        parties.save(party);
    }

    /** The partyId in a person verification's externalReference, if it came from an invite link. */
    public static Optional<String> partyIdFromReference(String externalReference) {
        return externalReference != null && externalReference.startsWith(PARTY_REFERENCE_PREFIX)
                ? Optional.of(externalReference.substring(PARTY_REFERENCE_PREFIX.length()))
                : Optional.empty();
    }

    /**
     * The OWNER_VERIFICATION check: screens required people (SCREENING), or combines their verifications
     * (FULL_KYC). Empty while a FULL_KYC person has no final result yet: the business then waits.
     */
    @Transactional
    public Optional<CheckResult> check(VerificationRequest business) {
        OwnerCheck mode = Optional.ofNullable(business.getMetadata().get(OWNER_CHECK)).map(Object::toString).map(OwnerCheck::valueOf)
                .orElseGet(() -> settings.effective(business.getCompany().getId()).ownerCheck());
        List<VerificationParty> people = parties.findByVerificationIdOrderByCreatedAtAsc(business.getId());
        List<VerificationParty> required = people.stream().filter(VerificationParty::isMustVerify).toList();

        if (mode == OwnerCheck.SCREENING) {
            required.stream().filter(p -> !p.getStatus().isFinal()).forEach(this::screen);
        } else if (mode == OwnerCheck.FULL_KYC && required.stream().anyMatch(p -> !p.getStatus().isFinal())) {
            return Optional.empty();
        }

        long passed = required.stream().filter(p -> p.getStatus() == PartyStatus.PASSED).count();
        long failed = required.stream().filter(p -> p.getStatus() == PartyStatus.FAILED).count();
        long review = required.stream().filter(p -> p.getStatus() == PartyStatus.REVIEW).count();
        String status = failed > 0 ? "failed" : review > 0 ? "review" : "passed";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode.name());
        result.put("required", required.size());
        result.put("passed", passed);
        result.put("failed", failed);
        result.put("review", review);
        result.put("people", people.stream().map(KybParties::summary).toList());
        if (failed > 0) result.put("reason", failed + " of " + required.size() + " directors and owners failed");
        else if (review > 0) result.put("reason", review + " of " + required.size() + " directors and owners need review");
        return Optional.of(CheckResult.builder().stepName(STEP).status(status).confidenceScore(null).result(result).build());
    }

    public static final String STEP = "owner_verification";

    private void screen(VerificationParty party) {
        try {
            RiskScreening.Screening screening = riskScreening.screen(RiskScreening.Subject.person(party.getId(),
                    party.getFirstName(), party.getLastName(),
                    party.getDateOfBirth() == null ? null : party.getDateOfBirth().toString()));
            Map<String, Object> facts = new HashMap<>();
            facts.put("verdict", screening.verdict().name());
            facts.put("hits", screening.hits());
            facts.put("topScore", screening.topScore());
            facts.put("pep", screening.pep());
            party.setScreening(facts);
            party.setStatus(switch (screening.verdict()) {
                case NO_HIT -> PartyStatus.PASSED;
                case POTENTIAL_MATCH -> PartyStatus.REVIEW;
                case CONFIRMED_MATCH -> PartyStatus.FAILED;
            });
        } catch (Exception e) {
            log.warn("Screening {} for business verification {} failed: {}", party.getId(), party.getVerificationId(), e.getMessage());
            party.setScreening(Map.of("error", "screening_unavailable"));
            party.setStatus(PartyStatus.REVIEW);
        }
        parties.save(party);
    }

    private static Map<String, Object> summary(VerificationParty p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.fullName());
        m.put("director", p.isDirector());
        m.put("ownershipPercent", p.getOwnershipPercent());
        m.put("required", p.isMustVerify());
        m.put("status", p.getStatus().name());
        if (p.getPersonVerificationId() != null) m.put("verificationId", p.getPersonVerificationId());
        return m;
    }

    /**
     * A person's verification reached its final result: record it on their business verification, and when nobody
     * required is still outstanding, put the business back into processing (it resumes from its saved checks).
     */
    @ApplicationModuleListener
    public void onOutcome(VerificationOutcome outcome) {
        parties.findByPersonVerificationId(outcome.verificationId()).ifPresent(party -> {
            boolean approved = outcome.status() == GlobalStatuses.COMPLETED && "approved".equalsIgnoreCase(outcome.overallDecision());
            boolean rejected = outcome.status() == GlobalStatuses.REJECTED || outcome.status() == GlobalStatuses.FAILED
                    || "rejected".equalsIgnoreCase(outcome.overallDecision());
            party.setStatus(approved ? PartyStatus.PASSED : rejected ? PartyStatus.FAILED : PartyStatus.REVIEW);
            parties.save(party);
            log.info("{} ({}) on business verification {}: {}", party.fullName(), party.getId(), party.getVerificationId(), party.getStatus());
            resumeIfReady(party.getVerificationId());
        });
    }

    private void resumeIfReady(String businessId) {
        verifications.findById(businessId).ifPresent(business -> {
            boolean waiting = business.getStatus() == GlobalStatuses.PENDING
                    && WAITING_FOR_PEOPLE.equals(business.getMetadata().get(WAITING));
            boolean everyoneDone = parties.findByVerificationIdOrderByCreatedAtAsc(businessId).stream()
                    .filter(VerificationParty::isMustVerify).allMatch(p -> p.getStatus().isFinal());
            if (waiting && everyoneDone) {
                business.getMetadata().remove(WAITING);
                business.getMetadata().put(com.intact_id.orchestration.verification.VerificationProcessor.RESUMED, true);
                business.markAsSubmitted();
                verifications.save(business);
                log.info("Business verification {} resumes: every required director and owner has a result", businessId);
            }
        });
    }

    /** Emails a person a link to complete their identity verification (FULL_KYC); returns the link. */
    @Transactional
    public String invite(String companyId, String businessId, String partyId) {
        VerificationParty party = parties.findById(partyId)
                .filter(p -> p.getVerificationId().equals(businessId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Director or owner not found"));
        VerificationRequest business = verifications.findById(businessId)
                .filter(b -> companyId == null || b.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Business verification not found"));
        if (!party.isMustVerify() || party.getStatus().isFinal()) {
            throw new ApplicationExceptions.IntactBadRequestException(party.fullName() + " doesn't need verifying");
        }
        String token = generatorUtils.generateUrlSafeToken(32);
        party.setInviteTokenHash(generatorUtils.hashString(token));
        party.setInviteExpiresAt(LocalDateTime.now().plusDays(INVITE_DAYS));
        if (party.getStatus() == PartyStatus.PENDING) party.setStatus(PartyStatus.INVITED);
        parties.save(party);

        String link = inviteUrl + "?token=" + token;
        if (party.getEmail() != null && !party.getEmail().isBlank()) {
            String companyName = Optional.ofNullable(business.getCustomerData().get("companyName")).map(Object::toString).orElse("a business");
            emailService.sendSimpleEmail(party.getEmail(), "Verify your identity for " + companyName,
                    "Hello " + party.getFirstName() + ",\n\n"
                            + business.getCompany().getLegalName() + " needs to verify the directors and owners of "
                            + companyName + ". Please complete your identity verification here:\n\n" + link + "\n\n"
                            + "The link works for " + INVITE_DAYS + " days.\n\nIntact ID");
        }
        return link;
    }

    /** The person behind a valid invite token, for starting their verification session. */
    public VerificationParty fromInvite(String token) {
        return parties.findByInviteTokenHash(generatorUtils.hashString(token))
                .filter(p -> p.getInviteExpiresAt() != null && p.getInviteExpiresAt().isAfter(LocalDateTime.now()))
                .filter(p -> !p.getStatus().isFinal())
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("This link has expired or was already used"));
    }

    public List<VerificationParty> forVerification(String businessId) {
        return new ArrayList<>(parties.findByVerificationIdOrderByCreatedAtAsc(businessId));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
