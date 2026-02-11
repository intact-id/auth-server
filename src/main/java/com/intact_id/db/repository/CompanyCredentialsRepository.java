package com.intact_id.db.repository;

import com.intact_id.db.model.CompanyCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 22/11/2025 14:18
 */

public interface CompanyCredentialsRepository extends JpaRepository<CompanyCredentials, String> {

    Optional<CompanyCredentials> findByCompanyId(String companyId);

    // Optional<CompanyCredentials> findByApiKeyHash(String apiKeyHash);

    @Query("SELECT cc FROM CompanyCredentials cc JOIN FETCH cc.company WHERE cc.apiKeyHash = :apiKeyHash")
    Optional<CompanyCredentials> findCompanyCredentialsByApiKeyHash(@Param("apiKeyHash") String apiKeyHash);

    @Query("SELECT cc FROM CompanyCredentials cc JOIN FETCH cc.company WHERE cc.devApiKeyHash = :apiKeyHash")
    Optional<CompanyCredentials> findCompanyCredentialsByDevApiKeyHash(@Param("apiKeyHash") String apiKeyHash);

    @Query("SELECT cc FROM CompanyCredentials cc JOIN FETCH cc.company WHERE cc.prodApiKeyHash = :apiKeyHash")
    Optional<CompanyCredentials> findCompanyCredentialsByProdApiKeyHash(@Param("apiKeyHash") String apiKeyHash);
}
