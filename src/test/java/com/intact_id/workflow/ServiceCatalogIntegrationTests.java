package com.intact_id.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs a database (like IntactIdApplicationTests): the Flyway baseline seeds the catalog Hibernate reads. */
@SpringBootTest
class ServiceCatalogIntegrationTests {

    @Autowired
    ServiceCatalog serviceCatalog;

    @Test
    void baselineCatalogShowsOnlyAvailableServicesToMerchants() {
        assertThat(serviceCatalog.available(SubjectType.INDIVIDUAL)).extracting(CatalogService::code)
                .containsExactly("DOCUMENT_CHECK", "DATA_CROSSCHECK", "LIVENESS", "FACE_MATCH", "SANCTIONS_SCREENING", "CRA");
        assertThat(serviceCatalog.available(SubjectType.BUSINESS)).extracting(CatalogService::code)
                .containsExactly("SANCTIONS_SCREENING", "CRA");

        CatalogService iprs = serviceCatalog.find("IPRS_LOOKUP").orElseThrow();
        assertThat(iprs.status()).isEqualTo(ServiceStatus.IN_DEVELOPMENT);
        assertThat(iprs.countries()).containsExactly("KEN");
        assertThat(serviceCatalog.find("LIVENESS").orElseThrow().outputs())
                .anySatisfy(o -> assertThat(o).containsEntry("key", "liveness.score"));
    }

    @Test
    void todaysFullKycFlowEvaluatesFromTheSeededCatalog() {
        ServiceRequirements r = serviceCatalog.requirementsFor(
                List.of("FACE_MATCH", "DATA_CROSSCHECK", "LIVENESS", "DOCUMENT_CHECK"), SubjectType.INDIVIDUAL, "KEN", true);

        assertThat(r.problems()).isEmpty();
        assertThat(r.services()).containsExactly("DOCUMENT_CHECK", "DATA_CROSSCHECK", "LIVENESS", "FACE_MATCH");
        assertThat(r.requiredFields()).containsExactly("firstName", "lastName", "dateOfBirth", "idNumber");
        assertThat(r.screens()).containsExactly("details", "document_capture", "selfie_liveness");
    }
}
