package com.intact_id.workflow.definition;

import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.PinnedWorkflow;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.WorkflowRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuntimeImpl implements WorkflowRuntime {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVersionRepository versions;
    private final ServiceCatalog catalog;

    @Override
    public PinnedWorkflow pin(String companyId, String workflowKey) {
        WorkflowDefinitionEntity definition = definitions.findByCompanyIdAndKeyAndArchivedFalse(companyId, workflowKey)
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Unknown workflow: " + workflowKey));
        if (definition.getLiveVersion() == null) {
            throw new ApplicationExceptions.IntactBadRequestException("Workflow " + workflowKey + " has not been published yet");
        }
        return find(definition.getId(), definition.getLiveVersion())
                .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Workflow version not found"));
    }

    @Override
    public Optional<PinnedWorkflow> pinDefault(String companyId) {
        return definitions.findByCompanyIdAndDefaultWorkflowTrueAndArchivedFalse(companyId)
                .filter(definition -> definition.getLiveVersion() != null)
                .flatMap(definition -> find(definition.getId(), definition.getLiveVersion()));
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
