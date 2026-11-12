package com.intact_id.checks;

import com.intact_id.checks.screening.ScreeningApi;
import com.intact_id.checks.screening.ScreeningServiceClient;
import com.intact_id.checks.screening.ScreeningTenants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Sanctions/PEP screening and customer risk assessment, done by sactions-service. */
@Service
@RequiredArgsConstructor
public class RiskScreening {

    private static final String PEP_TOPIC = "role.pep";
    private static final Set<String> ADVERSE_TOPICS = Set.of("sanction", "sanction.linked", "debarment", "poi", "wanted");

    private final ScreeningServiceClient client;
    private final ScreeningTenants tenants;

    public enum Verdict { NO_HIT, POTENTIAL_MATCH, CONFIRMED_MATCH }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    /** Who is screened: a person (names, birth date) or a business (name, jurisdiction). */
    public record Subject(boolean business, String customerId, String firstName, String lastName, String birthDate,
                          String businessName, String jurisdiction) {

        public static Subject person(String customerId, String firstName, String lastName, String birthDate) {
            return new Subject(false, customerId, firstName, lastName, birthDate, null, null);
        }

        public static Subject business(String customerId, String name, String jurisdiction) {
            return new Subject(true, customerId, null, null, null, name, jurisdiction);
        }
    }

    public record Match(String entityId, String caption, double score, List<String> datasets, List<String> topics) {
    }

    public record Screening(Verdict verdict, List<Match> matches) {

        public int hits() {
            return matches.size();
        }

        public double topScore() {
            return matches.stream().mapToDouble(Match::score).max().orElse(0.0);
        }

        public boolean pep() {
            return topics().anyMatch(PEP_TOPIC::equals);
        }

        public boolean adverseMedia() {
            return topics().anyMatch(t -> ADVERSE_TOPICS.contains(t) || t.startsWith("crime."));
        }

        private java.util.stream.Stream<String> topics() {
            return matches.stream().flatMap(m -> m.topics().stream());
        }
    }

    /** complete is false when the risk model was missing a required attribute; screening is null if not returned. */
    public record Assessment(String id, RiskLevel level, BigDecimal score, boolean complete, boolean overrideApplied,
                             Integer modelVersion, Screening screening) {
    }

    public Screening screen(Subject subject) {
        ScreeningApi.Result result = subject.business()
                ? client.matchBusiness(new ScreeningApi.BusinessMatch(subject.businessName(), subject.jurisdiction()))
                : client.matchPerson(new ScreeningApi.PersonMatch(subject.firstName(), subject.lastName(), null,
                subject.birthDate()));
        return toScreening(result);
    }

    /** Runs the company's active risk model; sactions-service screens the subject as part of it. */
    public Assessment assess(String companyId, String companyName, Subject subject, Map<String, Object> attributes) {
        String tenantId = tenants.tenantFor(companyId, companyName);
        ScreeningApi.AssessmentRequest request = new ScreeningApi.AssessmentRequest(
                tenantId, subject.customerId(), subject.business() ? "BUSINESS" : "INDIVIDUAL", "ONBOARDING",
                attributes, "ONBOARDING",
                subject.business() ? null : new ScreeningApi.PersonMatch(subject.firstName(), subject.lastName(), null,
                        subject.birthDate()),
                subject.business() ? new ScreeningApi.BusinessMatch(subject.businessName(), subject.jurisdiction()) : null);
        ScreeningApi.Assessment a = Optional.ofNullable(client.runAssessment(request))
                .map(ScreeningApi.Envelope::data)
                .orElseThrow(() -> new IllegalStateException("screening-service returned no assessment"));
        return new Assessment(a.id(), parse(RiskLevel.class, a.overallLevel()), a.overallScore(),
                "COMPLETE".equalsIgnoreCase(a.status()), Boolean.TRUE.equals(a.overrideApplied()), a.modelVersion(),
                a.screening() == null ? null : toScreening(a.screening()));
    }

    private static Screening toScreening(ScreeningApi.Result result) {
        List<Match> matches = Optional.ofNullable(result.matches()).orElse(List.of()).stream()
                .map(m -> new Match(m.entityId(), m.caption(), m.score(),
                        Optional.ofNullable(m.datasets()).orElse(List.of()),
                        Optional.ofNullable(m.topics()).orElse(List.of())))
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .toList();
        Verdict verdict = parse(Verdict.class, result.verdict());
        // An unknown verdict is never treated as clear
        return new Screening(verdict != null ? verdict : Verdict.POTENTIAL_MATCH, matches);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
