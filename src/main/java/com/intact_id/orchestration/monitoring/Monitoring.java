package com.intact_id.orchestration.monitoring;

import com.intact_id.accounts.NotificationService;
import com.intact_id.checks.RiskScreening;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationOutcome;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.events.VerificationWebhooks;
import com.intact_id.orchestration.kyb.VerificationParty;
import com.intact_id.orchestration.kyb.VerificationPartyRepository;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.monitoring.MonitoringSettingsEntity.Frequency;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ongoing monitoring: approved people, businesses and their directors and owners are screened again on a schedule.
 * The first screen is the baseline. After that each change is an alert: a new match, a match that became
 * sanctioned or PEP, or a match that went away. The company is called back (webhook screening.updated) and
 * confirms (it is them) or dismisses (it is not: that entry is then ignored unless it gains topics).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Monitoring {

    private static final int BATCH = 100;
    private static final int MAX_BATCHES = 20;

    private final MonitoringSettingsRepository settingsRepo;
    private final MonitoredSubjectRepository subjects;
    private final MonitoringAlertRepository alerts;
    private final RiskScreening screening;
    private final VerificationRequestRepository verifications;
    private final VerificationPartyRepository parties;
    private final VerificationWebhooks webhooks;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;

    public record Settings(boolean enabled, Frequency frequency, boolean includePeople, boolean includeBusinesses,
                           boolean includeParties, long activeSubjects, long openAlerts) {
    }

    public record SubjectDetail(MonitoredSubject subject, List<MonitoringAlert> alerts) {
    }

    // ----- settings -----

    public Settings settings(String companyId) {
        MonitoringSettingsEntity s = settingsRepo.findByCompanyId(companyId).orElseGet(MonitoringSettingsEntity::new);
        return new Settings(s.isEnabled(), s.getFrequency(), s.isIncludePeople(), s.isIncludeBusinesses(), s.isIncludeParties(),
                subjects.countByCompanyIdAndStatus(companyId, MonitoredSubject.Status.ACTIVE),
                alerts.countByCompanyIdAndStatus(companyId, MonitoringAlert.Status.OPEN));
    }

    @Transactional
    public Settings updateSettings(String companyId, Settings changes) {
        MonitoringSettingsEntity s = settingsRepo.findByCompanyId(companyId).orElseGet(MonitoringSettingsEntity::new);
        s.setCompanyId(companyId);
        s.setEnabled(changes.enabled());
        s.setFrequency(changes.frequency() == null ? Frequency.DAILY : changes.frequency());
        s.setIncludePeople(changes.includePeople());
        s.setIncludeBusinesses(changes.includeBusinesses());
        s.setIncludeParties(changes.includeParties());
        settingsRepo.save(s);
        log.info("Monitoring for company {}: {} ({})", companyId, s.isEnabled() ? "on" : "off", s.getFrequency());
        return settings(companyId);
    }

    // ----- enrolment -----

    /** An approved verification is enrolled when its company has monitoring on. */
    @ApplicationModuleListener
    public void onOutcome(VerificationOutcome outcome) {
        boolean approved = outcome.status() == GlobalStatuses.COMPLETED && "approved".equalsIgnoreCase(outcome.overallDecision());
        if (!approved) return;
        settingsRepo.findByCompanyId(outcome.companyId()).filter(MonitoringSettingsEntity::isEnabled).ifPresent(s ->
                verifications.findByIdWithCompany(outcome.verificationId()).ifPresent(v -> enrol(v, s, false)));
    }

    /** Starts monitoring a verification by hand (and its directors and owners, for a business). */
    @Transactional
    public List<MonitoredSubject> start(String companyId, String verificationId) {
        VerificationRequest v = verifications.findByIdWithCompany(verificationId)
                .filter(r -> companyId == null || r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Verification not found"));
        if (v.getStatus() != GlobalStatuses.COMPLETED || !"approved".equalsIgnoreCase(v.getOverallDecision())) {
            throw new ApplicationExceptions.IntactBadRequestException("Only an approved verification can be monitored");
        }
        if (v.getPersonalDataErasedAt() != null) {
            throw new ApplicationExceptions.IntactBadRequestException("This verification's personal data was erased; there is nothing to screen");
        }
        List<MonitoredSubject> started = enrol(v, settingsRepo.findByCompanyId(v.getCompany().getId()).orElseGet(MonitoringSettingsEntity::new), true);
        if (started.isEmpty()) throw new ApplicationExceptions.IntactBadRequestException("It is already being monitored");
        return started;
    }

    /** manual: enrol every kind; otherwise only the kinds the company's settings include. Returns what is new. */
    private List<MonitoredSubject> enrol(VerificationRequest v, MonitoringSettingsEntity s, boolean manual) {
        if (v.getPersonalDataErasedAt() != null) return List.of();
        Map<String, Object> data = Optional.ofNullable(v.getCustomerData()).orElse(Map.of());
        List<MonitoredSubject> created = new ArrayList<>();
        if ("business".equalsIgnoreCase(v.getVerificationType())) {
            if ((manual || s.isIncludeBusinesses()) && text(data.get("companyName")) != null
                    && subjects.findByVerificationIdAndPartyIdIsNull(v.getId()).isEmpty()) {
                MonitoredSubject b = base(v, null, MonitoredSubject.Kind.BUSINESS, text(data.get("companyName")));
                b.setBusinessName(text(data.get("companyName")));
                b.setJurisdiction(text(data.get("country")));
                created.add(subjects.save(b));
            }
            if (manual || s.isIncludeParties()) {
                for (VerificationParty p : parties.findByVerificationIdOrderByCreatedAtAsc(v.getId())) {
                    // A person with their own verification is monitored through that one
                    if (p.getPersonVerificationId() != null || subjects.findByVerificationIdAndPartyId(v.getId(), p.getId()).isPresent()) continue;
                    MonitoredSubject person = base(v, p.getId(), MonitoredSubject.Kind.PERSON, p.fullName());
                    person.setFirstName(p.getFirstName());
                    person.setLastName(p.getLastName());
                    person.setDateOfBirth(p.getDateOfBirth() == null ? null : p.getDateOfBirth().toString());
                    created.add(subjects.save(person));
                }
            }
        } else if ((manual || s.isIncludePeople()) && text(data.get("firstName")) != null
                && subjects.findByVerificationIdAndPartyIdIsNull(v.getId()).isEmpty()) {
            MonitoredSubject person = base(v, null, MonitoredSubject.Kind.PERSON,
                    (text(data.get("firstName")) + " " + Optional.ofNullable(text(data.get("lastName"))).orElse("")).trim());
            person.setFirstName(text(data.get("firstName")));
            person.setLastName(text(data.get("lastName")));
            person.setDateOfBirth(date(data.get("dateOfBirth")));
            created.add(subjects.save(person));
        }
        if (!created.isEmpty()) log.info("Monitoring {} subject(s) of verification {}", created.size(), v.getId());
        return created;
    }

    private static MonitoredSubject base(VerificationRequest v, String partyId, MonitoredSubject.Kind kind, String name) {
        MonitoredSubject s = new MonitoredSubject();
        s.setCompanyId(v.getCompany().getId());
        s.setVerificationId(v.getId());
        s.setPartyId(partyId);
        s.setExternalReference(v.getExternalReference());
        s.setKind(kind);
        s.setDisplayName(name);
        s.setStatus(MonitoredSubject.Status.ACTIVE);
        // The first screen (the baseline) happens on the next run
        s.setNextScreenAt(LocalDateTime.now());
        return s;
    }

    // ----- stopping -----

    @Transactional
    public MonitoredSubject stop(String companyId, String subjectId, String reason) {
        MonitoredSubject s = owned(companyId, subjectId);
        stop(s, reason == null || reason.isBlank() ? "Stopped" : reason.trim());
        return subjects.save(s);
    }

    /** The customer relationship ended: nothing of theirs is monitored any more. */
    @Transactional
    public void stopForVerification(String verificationId, String reason) {
        subjects.findByVerificationId(verificationId).stream().filter(s -> s.getStatus() == MonitoredSubject.Status.ACTIVE)
                .forEach(s -> {
                    stop(s, reason);
                    subjects.save(s);
                });
    }

    /** The verification's personal data was erased: the copy kept for screening goes with it, and its alerts. */
    @Transactional
    public void deleteForVerification(String verificationId) {
        subjects.deleteAll(subjects.findByVerificationId(verificationId));
    }

    private static void stop(MonitoredSubject s, String reason) {
        s.setStatus(MonitoredSubject.Status.STOPPED);
        s.setStoppedReason(reason);
        s.setStoppedAt(LocalDateTime.now());
        s.setNextScreenAt(null);
    }

    // ----- screening -----

    /** Screens every subject that is due. Every 15 minutes by default; each subject at its company's frequency. */
    @Scheduled(fixedDelayString = "${monitoring.check-ms:900000}", initialDelayString = "${monitoring.initial-delay-ms:120000}")
    public void run() {
        int screened = 0;
        int raised = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<MonitoredSubject> due = subjects.findDue(LocalDateTime.now(), PageRequest.of(0, BATCH));
            if (due.isEmpty()) break;
            for (MonitoredSubject s : due) {
                raised += screenSafely(s);
                screened++;
            }
        }
        if (screened > 0) log.info("Monitoring: re-screened {} subjects, {} alerts", screened, raised);
    }

    /** Screens one subject now, outside its schedule. */
    public SubjectDetail rescreen(String companyId, String subjectId) {
        MonitoredSubject s = owned(companyId, subjectId);
        if (s.getStatus() != MonitoredSubject.Status.ACTIVE) {
            throw new ApplicationExceptions.IntactBadRequestException("Monitoring of this subject has stopped");
        }
        List<MonitoringAlert> raised = screenOnce(s);
        publish(s, raised);
        return detail(companyId, subjectId);
    }

    private int screenSafely(MonitoredSubject s) {
        try {
            List<MonitoringAlert> raised = screenOnce(s);
            publish(s, raised);
            return raised.size();
        } catch (RuntimeException e) {
            // Screening unavailable: try again later, waiting longer each time (up to a day)
            s.setFailures(s.getFailures() + 1);
            s.setNextScreenAt(LocalDateTime.now().plusHours(Math.min(24, 1L << Math.min(s.getFailures(), 5))));
            subjects.save(s);
            log.warn("Re-screening {} failed ({} in a row): {}", s.getId(), s.getFailures(), e.getMessage());
            return 0;
        }
    }

    /** Screens, compares with the last result, saves the result and any alerts. Returns the alerts raised. */
    private List<MonitoringAlert> screenOnce(MonitoredSubject subject) {
        RiskScreening.Screening result = screening.screen(subject.getKind() == MonitoredSubject.Kind.BUSINESS
                ? RiskScreening.Subject.business(subject.getId(), subject.getBusinessName(), subject.getJurisdiction())
                : RiskScreening.Subject.person(subject.getId(), subject.getFirstName(), subject.getLastName(), subject.getDateOfBirth()));
        List<Map<String, Object>> current = result.matches().stream().map(Monitoring::match).toList();
        Frequency frequency = settingsRepo.findByCompanyId(subject.getCompanyId()).map(MonitoringSettingsEntity::getFrequency).orElse(Frequency.DAILY);

        return transactions.execute(status -> {
            MonitoredSubject s = subjects.findById(subject.getId()).orElse(subject);
            List<MonitoringAlert> raised = new ArrayList<>();
            boolean baseline = s.getVerdict() == null;
            if (!baseline) {
                raised.addAll(compare(s, current, result.verdict().name()));
            }
            s.setVerdict(result.verdict().name());
            s.setMatches(new ArrayList<>(current));
            s.setLastScreenedAt(LocalDateTime.now());
            s.setNextScreenAt(LocalDateTime.now().plusDays(frequency.days));
            s.setFailures(0);
            s.setOpenAlerts(s.getOpenAlerts() + (int) raised.stream().filter(a -> a.getStatus() == MonitoringAlert.Status.OPEN).count());
            subjects.save(s);
            raised.forEach(alerts::save);
            subject.setOpenAlerts(s.getOpenAlerts());
            return raised;
        });
    }

    /** The alerts between the last result and this one; dismissed entries only count again if they gain topics. */
    private List<MonitoringAlert> compare(MonitoredSubject s, List<Map<String, Object>> current, String verdict) {
        Map<String, Map<String, Object>> before = byEntity(s.getMatches());
        Map<String, Map<String, Object>> now = byEntity(current);
        Map<String, List<String>> dismissed = Optional.ofNullable(s.getDismissed()).orElse(Map.of());

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();
        now.forEach((entityId, match) -> {
            Set<String> topics = topics(match);
            Map<String, Object> previous = before.get(entityId);
            Set<String> known = new HashSet<>(previous != null ? topics(previous) : Set.of());
            if (dismissed.containsKey(entityId)) known.addAll(dismissed.get(entityId));
            Set<String> gained = new HashSet<>(topics);
            gained.removeAll(known);
            if (previous == null && !dismissed.containsKey(entityId)) {
                added.add(match);
            } else if (!gained.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>(match);
                m.put("newTopics", List.copyOf(gained));
                changed.add(m);
            }
        });
        List<Map<String, Object>> removed = before.entrySet().stream().filter(e -> !now.containsKey(e.getKey()))
                .map(Map.Entry::getValue).toList();

        List<MonitoringAlert> raised = new ArrayList<>();
        if (!added.isEmpty()) raised.add(alert(s, MonitoringAlert.Kind.NEW_MATCH, verdict, added, MonitoringAlert.Status.OPEN));
        if (!changed.isEmpty()) raised.add(alert(s, MonitoringAlert.Kind.MATCH_CHANGED, verdict, changed, MonitoringAlert.Status.OPEN));
        if (!removed.isEmpty()) raised.add(alert(s, MonitoringAlert.Kind.MATCH_REMOVED, verdict, removed, MonitoringAlert.Status.INFO));
        return raised;
    }

    private static MonitoringAlert alert(MonitoredSubject s, MonitoringAlert.Kind kind, String verdict,
                                         List<Map<String, Object>> matches, MonitoringAlert.Status status) {
        MonitoringAlert a = new MonitoringAlert();
        a.setCompanyId(s.getCompanyId());
        a.setSubjectId(s.getId());
        a.setKind(kind);
        a.setPreviousVerdict(s.getVerdict());
        a.setVerdict(verdict);
        a.setMatches(new ArrayList<>(matches));
        a.setStatus(status);
        return a;
    }

    /** Calls the company back and notifies it in the console, once the alerts are saved. */
    private void publish(MonitoredSubject s, List<MonitoringAlert> raised) {
        for (MonitoringAlert a : raised) {
            try {
                webhooks.screeningUpdated(a, s);
                if (a.getStatus() == MonitoringAlert.Status.OPEN) {
                    notifications.persistNotification(s.getCompanyId(), "Screening alert: " + s.getDisplayName(),
                            describe(a) + " Review it under Monitoring.", "WARNING");
                }
            } catch (RuntimeException e) {
                log.warn("Could not send monitoring alert {}: {}", a.getId(), e.getMessage());
            }
        }
    }

    private static String describe(MonitoringAlert a) {
        String names = a.getMatches().stream().map(m -> String.valueOf(m.get("caption"))).limit(3).reduce((x, y) -> x + ", " + y).orElse("");
        return switch (a.getKind()) {
            case NEW_MATCH -> "New possible match: " + names + ".";
            case MATCH_CHANGED -> "A match is now listed as " + a.getMatches().stream()
                    .flatMap(m -> ((List<?>) m.getOrDefault("newTopics", List.of())).stream()).map(String::valueOf).distinct()
                    .reduce((x, y) -> x + ", " + y).orElse("changed") + ": " + names + ".";
            case MATCH_REMOVED -> "No longer matches: " + names + ".";
        };
    }

    // ----- review -----

    /** CONFIRMED: it is them. DISMISSED: it is not; those entries are ignored from now on unless they gain topics. */
    @Transactional
    public MonitoringAlert resolve(String companyId, String alertId, MonitoringAlert.Status decision, String note, String by) {
        if (decision != MonitoringAlert.Status.CONFIRMED && decision != MonitoringAlert.Status.DISMISSED) {
            throw new ApplicationExceptions.IntactBadRequestException("decision must be CONFIRMED or DISMISSED");
        }
        MonitoringAlert a = alerts.findById(alertId).filter(x -> companyId == null || x.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Alert not found"));
        if (a.getStatus() != MonitoringAlert.Status.OPEN) {
            throw new ApplicationExceptions.IntactBadRequestException("This alert was already " + a.getStatus().name().toLowerCase());
        }
        a.setStatus(decision);
        a.setResolvedBy(by);
        a.setResolvedAt(LocalDateTime.now());
        a.setResolutionNote(note == null || note.isBlank() ? null : note.trim());
        subjects.findById(a.getSubjectId()).ifPresent(s -> {
            if (decision == MonitoringAlert.Status.DISMISSED) {
                Map<String, List<String>> dismissed = new HashMap<>(Optional.ofNullable(s.getDismissed()).orElse(Map.of()));
                a.getMatches().forEach(m -> dismissed.put(String.valueOf(m.get("entityId")), List.copyOf(topics(m))));
                s.setDismissed(dismissed);
            }
            s.setOpenAlerts(Math.max(0, s.getOpenAlerts() - 1));
            subjects.save(s);
        });
        log.info("Monitoring alert {} {} by {}", alertId, decision, by);
        return alerts.save(a);
    }

    // ----- reading -----

    public Page<MonitoredSubject> subjects(String companyId, MonitoredSubject.Status status, String search, int page, int size) {
        return subjects.search(companyId, status, search == null || search.isBlank() ? null : search.trim(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    public Page<MonitoringAlert> alerts(String companyId, MonitoringAlert.Status status, int page, int size) {
        return alerts.search(companyId, status, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    public SubjectDetail detail(String companyId, String subjectId) {
        MonitoredSubject s = owned(companyId, subjectId);
        return new SubjectDetail(s, alerts.findBySubjectIdOrderByCreatedAtDesc(s.getId()));
    }

    public List<SubjectDetail> forVerification(String companyId, String verificationId) {
        return subjects.findByVerificationId(verificationId).stream()
                .filter(s -> companyId == null || s.getCompanyId().equals(companyId))
                .map(s -> new SubjectDetail(s, alerts.findBySubjectIdOrderByCreatedAtDesc(s.getId()))).toList();
    }

    public long openAlertsEverywhere() {
        return alerts.countByStatus(MonitoringAlert.Status.OPEN);
    }

    private MonitoredSubject owned(String companyId, String subjectId) {
        return subjects.findById(subjectId).filter(s -> companyId == null || s.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Monitored subject not found"));
    }

    // ----- helpers -----

    private static Map<String, Object> match(RiskScreening.Match m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entityId", m.entityId());
        map.put("caption", m.caption());
        map.put("score", m.score());
        map.put("datasets", m.datasets() == null ? List.of() : m.datasets());
        map.put("topics", m.topics() == null ? List.of() : m.topics());
        return map;
    }

    private static Map<String, Map<String, Object>> byEntity(List<Map<String, Object>> matches) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (matches != null) matches.forEach(m -> map.put(String.valueOf(m.get("entityId")), m));
        return map;
    }

    private static Set<String> topics(Map<String, Object> match) {
        Set<String> set = new HashSet<>();
        if (match.get("topics") instanceof List<?> list) list.forEach(t -> set.add(String.valueOf(t)));
        return set;
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    /** Dates arrive as "1990-05-01" or, from older rows, as [1990, 5, 1]. */
    private static String date(Object value) {
        if (value instanceof List<?> parts && parts.size() == 3) {
            return String.format("%04d-%02d-%02d", ((Number) parts.get(0)).intValue(), ((Number) parts.get(1)).intValue(),
                    ((Number) parts.get(2)).intValue());
        }
        return text(value);
    }

}
