package com.intact_id.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs a database (like IntactIdApplicationTests): the migration seeds the catalog Hibernate reads. */
@SpringBootTest
class ServiceCatalogIntegrationTests {

    @Autowired
    DataSource dataSource;

    @Autowired
    ServiceCatalog serviceCatalog;

    @BeforeEach
    void seed() {
        // Run twice: the migration must be safe to repeat
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migrations/2026-11-09_add_workflow_service_catalog.sql"));
        populator.execute(dataSource);
        populator.execute(dataSource);
    }

    @Test
    void seededCatalogShowsOnlyAvailableServicesToMerchants() {
        assertThat(serviceCatalog.available(SubjectType.INDIVIDUAL)).extracting(CatalogService::code)
                .containsExactly("DOCUMENT_CHECK", "DATA_CROSSCHECK", "LIVENESS", "FACE_MATCH");
        assertThat(serviceCatalog.available(SubjectType.BUSINESS)).isEmpty();

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
