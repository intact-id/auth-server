package com.intact_id.workflow.definition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, String> {

    List<WorkflowDefinitionEntity> findByCompanyIdAndArchivedFalseOrderByNameAsc(String companyId);

    List<WorkflowDefinitionEntity> findByCompanyIdIsNullAndArchivedFalseOrderByNameAsc();

    List<WorkflowDefinitionEntity> findByCompanyIdAndArchivedTrueOrderByNameAsc(String companyId);

    List<WorkflowDefinitionEntity> findByCompanyIdIsNullAndArchivedTrueOrderByNameAsc();

    boolean existsByCompanyIdAndKey(String companyId, String key);

    boolean existsByCompanyIdIsNullAndKey(String key);

    Optional<WorkflowDefinitionEntity> findByCompanyIdAndKeyAndArchivedFalse(String companyId, String key);

    Optional<WorkflowDefinitionEntity> findByCompanyIdAndDefaultWorkflowTrueAndArchivedFalse(String companyId);
}
