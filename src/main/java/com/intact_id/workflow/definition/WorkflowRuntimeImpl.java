package com.intact_id.workflow.definition;

import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.PinnedWorkflow;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.WorkflowRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkflowRuntimeImpl implements WorkflowRuntime {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVersionRepository versions;
    private final ServiceCatalog catalog;

    @Override
    public PinnedWorkflow pin(String companyId, String workflowKey, boolean live) {
        WorkflowDefinitionEntity definition = definitions.findByCompanyIdAndKeyAndArchivedFalse(companyId, workflowKey)
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Unknown workflow: " + workflowKey));
        Integer number = live ? definition.getLiveVersion()
                : (definition.getLatestVersion() > 0 ? definition.getLatestVersion() : null);
        if (number == null) {
            throw new ApplicationExceptions.IntactBadRequestException(live
                    ? "Workflow " + workflowKey + " has no live version; promote one first"
                    : "Workflow " + workflowKey + " has not been published yet");
        }
        return find(definition.getId(), number)
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Workflow version not found"));
    }

    @Override
    public Optional<PinnedWorkflow> find(String workflowId, int version) {
        return definitions.findById(workflowId).flatMap(definition -> versions.findByDefinitionIdAndNumber(workflowId, version)
                .map(v -> new PinnedWorkflow(definition.getId(), definition.getKey(), v.getNumber(),
                        definition.getSubjectType(), v.getGraph(), v.getServices(),
                        // Deprecated services keep running in versions that already use them
                        catalog.requirementsFor(v.getServices(), definition.getSubjectType(), null, false))));
    }
}
