package com.intact_id.service.CompanyService;

import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyApplication;
import org.springframework.stereotype.Service;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:04
 */

@Service
public interface CompanyService {

    Company createCompanyFromApplication(CompanyApplication application);

    Company getCompanyById(String companyId);

    Company getCompanyByCustomerId(String customerId);

    void suspendCompany(String companyId, String adminId, String reason);

    void activateCompany(String companyId, String adminId);
}
