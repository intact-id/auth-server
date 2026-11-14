package com.intact_id.orchestration.privacy;

import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Retention rules. Intact sets limits per country and kind of data (minimum, maximum, default, when the clock
 * starts, legal basis); companies choose their own periods within them, per country or for every country.
 * Changing a rule replans the affected verifications, so the nightly run picks up the new dates.
 */
@Service
@RequiredArgsConstructor
public class RetentionRules {

    private static final int MAX_DAYS = 36500;

    private final RetentionCountryRuleRepository countryRules;
    private final RetentionCompanyRuleRepository companyRules;
    private final VerificationRequestRepository verifications;

    /** Intact's limits for one kind of data in one country. */
    public record CountryLimit(Integer minDays, Integer maxDays, Integer defaultDays, StartsFrom startsFrom, String legalBasis) {
    }

    public record CountryView(String country, String name, Map<RetentionCategory, CountryLimit> categories) {
    }

    /**
     * What applies to one kind of data: days null = kept (no rule and no maximum). source is COMPANY_COUNTRY,
     * COMPANY_ALL, COUNTRY_DEFAULT or NONE; adjusted is true when a company's period was moved into the limits.
     */
    public record EffectiveRule(RetentionCategory category, Integer days, StartsFrom startsFrom, Integer minDays,
                                Integer maxDays, String legalBasis, String source, boolean adjusted) {
    }

    public record CompanyCountryView(String country, String name, Map<RetentionCategory, Integer> days,
                                     Map<RetentionCategory, EffectiveRule> effective) {
    }

    // ----- country limits (super admins) -----

    public List<CountryView> countries() {
        Map<String, Map<RetentionCategory, CountryLimit>> byCountry = new java.util.TreeMap<>((a, b) ->
                Countries.ANY.equals(a) ? -1 : Countries.ANY.equals(b) ? 1 : a.compareTo(b));
        for (RetentionCountryRule r : countryRules.findAllByOrderByCountryAscCategoryAsc()) {
            byCountry.computeIfAbsent(r.getCountry(), c -> new EnumMap<>(RetentionCategory.class)).put(r.getCategory(), limit(r));
        }
        byCountry.computeIfAbsent(Countries.ANY, c -> new EnumMap<>(RetentionCategory.class));
        return byCountry.entrySet().stream().map(e -> new CountryView(e.getKey(), Countries.name(e.getKey()), e.getValue())).toList();
    }

    /** Replaces a country's limits; a kind of data left out has no limits there (the '*' row then applies). */
    @Transactional
    public CountryView updateCountry(String country, Map<RetentionCategory, CountryLimit> limits) {
        String code = Countries.ruleCountry(country);
        limits.forEach(RetentionRules::validate);
        List<RetentionCountryRule> existing = countryRules.findByCountryIn(List.of(code));
        for (RetentionCategory category : RetentionCategory.values()) {
            CountryLimit limit = limits.get(category);
            Optional<RetentionCountryRule> row = existing.stream().filter(r -> r.getCategory() == category).findFirst();
            if (limit == null && !Countries.ANY.equals(code)) {
                row.ifPresent(countryRules::delete);
                continue;
            }
            RetentionCountryRule rule = row.orElseGet(RetentionCountryRule::new);
            CountryLimit l = limit == null ? new CountryLimit(null, null, null, StartsFrom.FINISHED, null) : limit;
            rule.setCountry(code);
            rule.setCategory(category);
            rule.setMinDays(l.minDays());
            rule.setMaxDays(l.maxDays());
            rule.setDefaultDays(l.defaultDays());
            rule.setStartsFrom(l.startsFrom() == null ? StartsFrom.FINISHED : l.startsFrom());
            rule.setLegalBasis(l.legalBasis() == null || l.legalBasis().isBlank() ? null : l.legalBasis().trim());
            countryRules.save(rule);
        }
        replanCountry(code);
        return countries().stream().filter(v -> v.country().equals(code)).findFirst().orElseThrow();
    }

    /** A country without its own limits falls back to the '*' row. */
    @Transactional
    public void deleteCountry(String country) {
        String code = Countries.ruleCountry(country);
        if (Countries.ANY.equals(code)) {
            throw new ApplicationExceptions.IntactBadRequestException("The rule for any other country can be changed, not removed");
        }
        countryRules.deleteByCountry(code);
        replanCountry(code);
    }

    // ----- company periods -----

    public List<CompanyCountryView> company(String companyId) {
        List<RetentionCompanyRule> own = companyRules.findByCompanyIdOrderByCountryAscCategoryAsc(companyId);
        TreeSet<String> shown = new TreeSet<>((a, b) -> Countries.ANY.equals(a) ? -1 : Countries.ANY.equals(b) ? 1 : a.compareTo(b));
        shown.add(Countries.ANY);
        own.forEach(r -> shown.add(r.getCountry()));
        countryRules.findAllByOrderByCountryAscCategoryAsc().forEach(r -> shown.add(r.getCountry()));
        Resolver resolver = resolver(companyId);
        List<CompanyCountryView> views = new ArrayList<>();
        for (String country : shown) {
            Map<RetentionCategory, Integer> days = new EnumMap<>(RetentionCategory.class);
            own.stream().filter(r -> r.getCountry().equals(country)).forEach(r -> days.put(r.getCategory(), r.getDays()));
            Map<RetentionCategory, EffectiveRule> effective = new EnumMap<>(RetentionCategory.class);
            for (RetentionCategory category : RetentionCategory.values()) {
                effective.put(category, resolver.effective(country, category));
            }
            views.add(new CompanyCountryView(country, Countries.name(country), days, effective));
        }
        return views;
    }

    /** Sets the company's periods for a country ('*' = every country); a kind of data left out uses Intact's default. */
    @Transactional
    public List<CompanyCountryView> updateCompany(String companyId, String country, Map<RetentionCategory, Integer> days) {
        String code = Countries.ruleCountry(country);
        Resolver limits = resolver(null);
        for (Map.Entry<RetentionCategory, Integer> e : days.entrySet()) {
            if (e.getValue() == null) continue;
            EffectiveRule limit = limits.effective(code, e.getKey());
            String label = humanize(e.getKey()) + " in " + Countries.name(code);
            if (e.getValue() < 0 || e.getValue() > MAX_DAYS) {
                throw new ApplicationExceptions.IntactBadRequestException(label + ": days must be between 0 and " + MAX_DAYS);
            }
            if (limit.minDays() != null && e.getValue() < limit.minDays()) {
                throw new ApplicationExceptions.IntactBadRequestException(label + " must be kept at least " + limit.minDays()
                        + " days" + (limit.legalBasis() != null ? " (" + limit.legalBasis() + ")" : ""));
            }
            if (limit.maxDays() != null && e.getValue() > limit.maxDays()) {
                throw new ApplicationExceptions.IntactBadRequestException(label + " may be kept at most " + limit.maxDays()
                        + " days" + (limit.legalBasis() != null ? " (" + limit.legalBasis() + ")" : ""));
            }
        }
        companyRules.deleteByCompanyIdAndCountry(companyId, code);
        companyRules.flush();
        days.forEach((category, value) -> {
            if (value == null) return;
            RetentionCompanyRule rule = new RetentionCompanyRule();
            rule.setCompanyId(companyId);
            rule.setCountry(code);
            rule.setCategory(category);
            rule.setDays(value);
            companyRules.save(rule);
        });
        verifications.replanCompany(companyId);
        return company(companyId);
    }

    // ----- resolving -----

    /** The rules for one company (or Intact's alone when companyId is null), loaded once for many lookups. */
    public Resolver resolver(String companyId) {
        List<RetentionCountryRule> limits = countryRules.findAll();
        List<RetentionCompanyRule> own = companyId == null ? List.of() : companyRules.findByCompanyIdOrderByCountryAscCategoryAsc(companyId);
        return new Resolver(limits, own);
    }

    public static final class Resolver {
        private final List<RetentionCountryRule> limits;
        private final List<RetentionCompanyRule> own;

        Resolver(List<RetentionCountryRule> limits, List<RetentionCompanyRule> own) {
            this.limits = limits;
            this.own = own;
        }

        public EffectiveRule effective(String country, RetentionCategory category) {
            String code = country == null ? Countries.ANY : country;
            Optional<RetentionCountryRule> limit = find(limits, code, category).or(() -> find(limits, Countries.ANY, category));
            Integer min = limit.map(RetentionCountryRule::getMinDays).orElse(null);
            Integer max = limit.map(RetentionCountryRule::getMaxDays).orElse(null);
            StartsFrom startsFrom = limit.map(RetentionCountryRule::getStartsFrom).orElse(StartsFrom.FINISHED);
            String basis = limit.map(RetentionCountryRule::getLegalBasis).orElse(null);

            Optional<Integer> forCountry = own.stream().filter(r -> r.getCountry().equals(code) && r.getCategory() == category)
                    .map(RetentionCompanyRule::getDays).findFirst();
            Optional<Integer> forAll = own.stream().filter(r -> r.getCountry().equals(Countries.ANY) && r.getCategory() == category)
                    .map(RetentionCompanyRule::getDays).findFirst();
            Integer days;
            String source;
            if (forCountry.isPresent() && !Countries.ANY.equals(code)) {
                days = forCountry.get();
                source = "COMPANY_COUNTRY";
            } else if (forAll.isPresent()) {
                days = forAll.get();
                source = "COMPANY_ALL";
            } else if (limit.map(RetentionCountryRule::getDefaultDays).isPresent()) {
                days = limit.get().getDefaultDays();
                source = "COUNTRY_DEFAULT";
            } else {
                days = max;
                source = max == null ? "NONE" : "COUNTRY_DEFAULT";
            }
            // A company's period for every country can fall outside one country's limits: move it inside
            Integer chosen = days;
            if (days != null && min != null && days < min) days = min;
            if (days != null && max != null && days > max) days = max;
            return new EffectiveRule(category, days, startsFrom, min, max, basis, source, !Objects.equals(chosen, days));
        }

        private static Optional<RetentionCountryRule> find(List<RetentionCountryRule> rules, String country, RetentionCategory category) {
            return rules.stream().filter(r -> r.getCountry().equals(country) && r.getCategory() == category).findFirst();
        }
    }

    private void replanCountry(String code) {
        if (Countries.ANY.equals(code)) verifications.replanAll();
        else verifications.replanCountry(code);
    }

    private static void validate(RetentionCategory category, CountryLimit l) {
        for (Integer d : new Integer[]{l.minDays(), l.maxDays(), l.defaultDays()}) {
            if (d != null && (d < 0 || d > MAX_DAYS)) {
                throw new ApplicationExceptions.IntactBadRequestException(humanize(category) + ": days must be between 0 and " + MAX_DAYS);
            }
        }
        if (l.minDays() != null && l.maxDays() != null && l.minDays() > l.maxDays()) {
            throw new ApplicationExceptions.IntactBadRequestException(humanize(category) + ": the minimum is more than the maximum");
        }
        if (l.defaultDays() != null && (l.minDays() != null && l.defaultDays() < l.minDays() || l.maxDays() != null && l.defaultDays() > l.maxDays())) {
            throw new ApplicationExceptions.IntactBadRequestException(humanize(category) + ": the default must be within the minimum and maximum");
        }
    }

    private static CountryLimit limit(RetentionCountryRule r) {
        return new CountryLimit(r.getMinDays(), r.getMaxDays(), r.getDefaultDays(), r.getStartsFrom(), r.getLegalBasis());
    }

    static String humanize(RetentionCategory category) {
        return switch (category) {
            case BIOMETRICS -> "Biometrics";
            case DOCUMENTS -> "Documents";
            case PERSONAL_DATA -> "Personal details";
            case RECORD -> "The verification record";
        };
    }
}
