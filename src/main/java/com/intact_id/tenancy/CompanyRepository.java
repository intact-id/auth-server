package com.intact_id.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 16/11/2025 16:37
 */

public interface CompanyRepository extends JpaRepository<Company, String>, JpaSpecificationExecutor<Company> {
   Optional<Company> findByCompanyIdentifierCode(String customerId);
}
