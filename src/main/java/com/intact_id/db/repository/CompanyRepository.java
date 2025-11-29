package com.intact_id.db.repository;

import com.intact_id.db.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 16/11/2025 16:37
 */

public interface CompanyRepository extends JpaRepository<Company, String>,
      org.springframework.data.jpa.repository.JpaSpecificationExecutor<Company> {
   Optional<Company> findByCompanyIdentifierCode(String customerId);
}
