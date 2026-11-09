package com.intact_id.workflow.definition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, String> {

    List<WorkflowVersionEntity> findByDefinitionIdOrderByNumberDesc(String definitionId);

    Optional<WorkflowVersionEntity> findByDefinitionIdAndNumber(String definitionId, int number);
}
