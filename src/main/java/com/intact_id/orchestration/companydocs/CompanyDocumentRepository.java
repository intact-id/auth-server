package com.intact_id.orchestration.companydocs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, String> {

    List<CompanyDocument> findByCompanyIdOrderByTypeAscVersionDesc(String companyId);

    Optional<CompanyDocument> findFirstByCompanyIdAndTypeOrderByVersionDesc(String companyId, CompanyDocumentType type);
}
