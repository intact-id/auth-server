package com.intact_id.db.repository;

import com.intact_id.db.model.CompanyCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:18
 */

public interface CompanyCredentialsRepository extends JpaRepository<CompanyCredentials,String> {

    Optional<CompanyCredentials> findByCompanyId(String companyId);

}
