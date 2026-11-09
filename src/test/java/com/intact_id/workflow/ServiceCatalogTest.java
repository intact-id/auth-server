package com.intact_id.workflow;

import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.catalog.CatalogAdminService;
import com.intact_id.workflow.catalog.CatalogServiceEntity;
import com.intact_id.workflow.catalog.CatalogServiceRepository;
import com.intact_id.workflow.catalog.ServiceCatalogImpl;
import com.intact_id.workflow.web.CatalogServiceRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceCatalogTest {

    final Map<String, CatalogServiceEntity> rows = new HashMap<>();
    final CatalogServiceRepository repository = mock(CatalogServiceRepository.class);
    ServiceCatalogImpl catalog;
    CatalogAdminService admin;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(repository.findByCode(anyString())).thenAnswer(i -> Optional.ofNullable(rows.get(i.<String>getArgument(0))));
        when(repository.existsByCode(anyString())).thenAnswer(i -> rows.containsKey(i.<String>getArgument(0)));
        when(repository.save(any())).thenAnswer(i -> {
            CatalogServiceEntity e = i.getArgument(0);
            rows.put(e.getCode(), e);
            return e;
        });
        catalog = new ServiceCatalogImpl(repository);
        admin = new CatalogAdminService(repository, catalog);

        add("DOCUMENT_CHECK", ServiceStatus.AVAILABLE, 10, List.of(), List.of(), List.of("id_front", "id_back"), "document_capture", List.of());
        add("DATA_CROSSCHECK", ServiceStatus.AVAILABLE, 20, List.of("DOCUMENT_CHECK"), List.of("firstName", "idNumber"), List.of(), null, List.of());
        add("LIVENESS", ServiceStatus.AVAILABLE, 30, List.of(), List.of(), List.of("selfie", "liveness_frames"), "selfie_liveness", List.of());
        add("FACE_MATCH", ServiceStatus.AVAILABLE, 40, List.of("DOCUMENT_CHECK", "LIVENESS"), List.of(), List.of(), null, List.of());
        add("IPRS_LOOKUP", ServiceStatus.IN_DEVELOPMENT, 60, List.of(), List.of("idNumber", "dateOfBirth"), List.of(), null, List.of("KEN"));
    }

    @Test
    void ordersDependenciesFirstAndCollectsWhatTheWidgetNeeds() {
        ServiceRequirements r = catalog.requirementsFor(List.of("FACE_MATCH", "LIVENESS", "DATA_CROSSCHECK", "DOCUMENT_CHECK"),
                "INDIVIDUAL", "KEN", true);

        assertThat(r.valid()).isTrue();
        assertThat(r.services()).containsExactly("DOCUMENT_CHECK", "DATA_CROSSCHECK", "LIVENESS", "FACE_MATCH");
        assertThat(r.requiredFields()).containsExactly("firstName", "idNumber");
        assertThat(r.captures()).containsExactly("id_front", "id_back", "selfie", "liveness_frames");
        assertThat(r.screens()).containsExactly("details", "document_capture", "selfie_liveness");
    }

    @Test
    void reportsMissingDependenciesUnavailableServicesAndCountries() {
        ServiceRequirements r = catalog.requirementsFor(List.of("FACE_MATCH", "IPRS_LOOKUP", "NFC_CHIP"), "INDIVIDUAL", "NGA", true);

        assertThat(r.valid()).isFalse();
        assertThat(r.problems()).anySatisfy(p -> assertThat(p).contains("FACE_MATCH needs DOCUMENT_CHECK"))
                .anySatisfy(p -> assertThat(p).contains("FACE_MATCH needs LIVENESS"))
                .anySatisfy(p -> assertThat(p).contains("IPRS_LOOKUP is not available"))
                .anySatisfy(p -> assertThat(p).contains("not available for NGA"))
                .anySatisfy(p -> assertThat(p).contains("NFC_CHIP is not in the service catalog"));
    }

    @Test
    void deprecatedServicesStillRunButCantBeAddedToNewWorkflows() {
        rows.get("LIVENESS").setStatus(ServiceStatus.DEPRECATED);
        refresh();

        assertThat(catalog.requirementsFor(List.of("LIVENESS"), null, null, false).valid()).isTrue();
        assertThat(catalog.requirementsFor(List.of("LIVENESS"), null, null, true).valid()).isFalse();
        assertThat(catalog.available(null)).extracting(CatalogService::code).doesNotContain("LIVENESS", "IPRS_LOOKUP");
    }

    @Test
    void newServicesStartHiddenAndFollowTheLifecycle() {
        CatalogServiceRequestDto request = new CatalogServiceRequestDto();
        request.setCode("NFC_CHIP");
        request.setName("NFC chip read");
        request.setCategory("VERIFICATION");
        request.setRequires(List.of("DOCUMENT_CHECK"));

        assertThat(admin.create(request).status()).isEqualTo(ServiceStatus.IN_DEVELOPMENT);
        assertThat(catalog.available(null)).extracting(CatalogService::code).doesNotContain("NFC_CHIP");

        assertThat(admin.changeStatus("NFC_CHIP", ServiceStatus.AVAILABLE).status()).isEqualTo(ServiceStatus.AVAILABLE);
        assertThat(catalog.available(null)).extracting(CatalogService::code).contains("NFC_CHIP");

        assertThatThrownBy(() -> admin.changeStatus("NFC_CHIP", ServiceStatus.IN_DEVELOPMENT))
                .isInstanceOf(ApplicationExceptions.InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> admin.delete("NFC_CHIP"))
                .isInstanceOf(ApplicationExceptions.InvalidStatusTransitionException.class);
    }

    @Test
    void aServiceCantBeAvailableBeforeItsDependencies() {
        rows.get("LIVENESS").setStatus(ServiceStatus.IN_DEVELOPMENT);
        rows.get("FACE_MATCH").setStatus(ServiceStatus.IN_DEVELOPMENT);
        refresh();

        assertThatThrownBy(() -> admin.changeStatus("FACE_MATCH", ServiceStatus.AVAILABLE))
                .hasMessageContaining("needs LIVENESS");
    }

    @Test
    void aServiceOthersDependOnCantBeDeprecated() {
        assertThatThrownBy(() -> admin.changeStatus("DOCUMENT_CHECK", ServiceStatus.DEPRECATED))
                .hasMessageContaining("depend on DOCUMENT_CHECK");
    }

    @Test
    void rejectsDependencyCycles() {
        CatalogServiceRequestDto request = new CatalogServiceRequestDto();
        request.setRequires(List.of("FACE_MATCH"));

        assertThatThrownBy(() -> admin.update("LIVENESS", request)).hasMessageContaining("cycle");
    }

    private void add(String code, ServiceStatus status, int order, List<String> requires, List<String> fields,
                     List<String> captures, String screen, List<String> countries) {
        Map<String, Object> ui = new HashMap<>();
        if (screen != null) ui.put("screen", screen);
        rows.put(code, CatalogServiceEntity.builder()
                .code(code).name(code).category("VERIFICATION").status(status).sortOrder(order)
                .requires(new ArrayList<>(requires)).requiredFields(new ArrayList<>(fields))
                .captures(new ArrayList<>(captures)).ui(ui).countries(new ArrayList<>(countries))
                .build());
    }

    private void refresh() {
        // Status changes in these tests bypass the admin service, so rebuild the cache
        catalog = new ServiceCatalogImpl(repository);
        admin = new CatalogAdminService(repository, catalog);
    }
}
