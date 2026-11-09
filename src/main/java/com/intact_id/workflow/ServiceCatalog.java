package com.intact_id.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The services workflows are built from; read at runtime, so changes apply without a deploy. */
public interface ServiceCatalog {

    /** Services merchants can add to workflows, optionally only those for a subject type. */
    List<CatalogService> available(SubjectType subjectType);

    Optional<CatalogService> find(String code);

    /**
     * Order, inputs and screens for running these services, with any problems. With availableOnly, services
     * that aren't AVAILABLE are problems (building a workflow); without, DEPRECATED ones still run (runtime).
     */
    ServiceRequirements requirementsFor(Collection<String> codes, SubjectType subjectType, String country, boolean availableOnly);
}
