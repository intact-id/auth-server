package com.intact_id.workflow.catalog;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intact_id.workflow.CatalogService;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.ServiceRequirements;
import com.intact_id.workflow.ServiceStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ServiceCatalogImpl implements ServiceCatalog {

    private static final String DETAILS_SCREEN = "details";
    private static final String ALL = "all";

    private final CatalogServiceRepository repository;
    // The catalog is small and read on every evaluation; other instances see changes within a minute
    private final LoadingCache<String, Map<String, CatalogService>> cache;

    public ServiceCatalogImpl(CatalogServiceRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build(key -> load());
    }

    @Override
    public List<CatalogService> available(String subjectType) {
        return all().values().stream()
                .filter(s -> s.status() == ServiceStatus.AVAILABLE)
                .filter(s -> s.supportsSubject(subjectType))
                .toList();
    }

    @Override
    public Optional<CatalogService> find(String code) {
        return Optional.ofNullable(code).map(c -> all().get(c));
    }

    @Override
    public ServiceRequirements requirementsFor(Collection<String> codes, String subjectType, String country,
                                               boolean availableOnly) {
        Map<String, CatalogService> catalog = all();
        List<String> problems = new ArrayList<>();
        Map<String, CatalogService> selected = new LinkedHashMap<>();

        for (String code : new LinkedHashSet<>(codes)) {
            CatalogService service = catalog.get(code);
            if (service == null) {
                problems.add(code + " is not in the service catalog");
                continue;
            }
            selected.put(code, service);
            if (service.status() == ServiceStatus.RETIRED
                    || service.status() == ServiceStatus.IN_DEVELOPMENT
                    || (availableOnly && service.status() != ServiceStatus.AVAILABLE)) {
                problems.add(service.name() + " is not available (" + service.status() + ")");
            }
            if (!service.supportsSubject(subjectType)) {
                problems.add(service.name() + " does not support " + subjectType + " applicants");
            }
            if (!service.supportsCountry(country)) {
                problems.add(service.name() + " is not available for " + country);
            }
        }
        for (CatalogService service : selected.values()) {
            for (String dependency : service.requires()) {
                if (!selected.containsKey(dependency)) {
                    String dependencyName = Optional.ofNullable(catalog.get(dependency)).map(CatalogService::name).orElse(dependency);
                    problems.add(service.name() + " needs " + dependencyName);
                }
            }
        }

        List<CatalogService> ordered = order(selected);
        Set<String> fields = new LinkedHashSet<>();
        Set<String> captures = new LinkedHashSet<>();
        Set<String> screens = new LinkedHashSet<>();
        ordered.forEach(s -> {
            fields.addAll(s.requiredFields());
            captures.addAll(s.captures());
        });
        // Fields are collected on one details screen before any capture
        if (!fields.isEmpty()) {
            screens.add(DETAILS_SCREEN);
        }
        ordered.stream()
                .map(s -> s.ui().get("screen"))
                .filter(screen -> screen instanceof String text && !text.isBlank())
                .forEach(screen -> screens.add((String) screen));

        return new ServiceRequirements(
                ordered.stream().map(CatalogService::code).toList(),
                List.copyOf(fields), List.copyOf(captures), List.copyOf(screens), List.copyOf(problems));
    }

    /** Clears the cache after a catalog change on this instance. */
    void refresh() {
        cache.invalidateAll();
    }

    Map<String, CatalogService> all() {
        return cache.get(ALL);
    }

    private Map<String, CatalogService> load() {
        Map<String, CatalogService> services = new LinkedHashMap<>();
        repository.findAll().stream()
                .map(CatalogServiceEntity::toView)
                .sorted(Comparator.comparingInt(CatalogService::sortOrder).thenComparing(CatalogService::code))
                .forEach(s -> services.put(s.code(), s));
        return Collections.unmodifiableMap(services);
    }

    /** Dependencies first; otherwise by catalog sort order. */
    private static List<CatalogService> order(Map<String, CatalogService> selected) {
        List<CatalogService> remaining = selected.values().stream()
                .sorted(Comparator.comparingInt(CatalogService::sortOrder).thenComparing(CatalogService::code))
                .toList();
        List<CatalogService> ordered = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        while (ordered.size() < remaining.size()) {
            boolean progressed = false;
            for (CatalogService service : remaining) {
                if (placed.contains(service.code())) continue;
                boolean ready = service.requires().stream().allMatch(d -> placed.contains(d) || !selected.containsKey(d));
                if (ready) {
                    ordered.add(service);
                    placed.add(service.code());
                    progressed = true;
                }
            }
            if (!progressed) {
                // A dependency cycle can't be stored (the admin service rejects it); keep order stable anyway
                remaining.stream().filter(s -> !placed.contains(s.code())).forEach(ordered::add);
                break;
            }
        }
        return ordered;
    }
}
