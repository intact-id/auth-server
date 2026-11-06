package com.intact_id.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 22/11/2025 14:16
 */

public interface CompanyCommunicationRepository extends JpaRepository<CompanyCommunicationDetails, String> {
    java.util.Optional<CompanyCommunicationDetails> findByCompanyId(String companyId);
}
