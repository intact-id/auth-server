package com.intact_id.accounts;

import com.intact_id.constant.enums.GlobalStatuses;
import org.springframework.stereotype.Service;


/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 23/11/2025 14:23
 */

@Service
public interface CompanyCredentialService {

    CompanyCredentials getByApiKeyCompanyCredentials(String apiKey);

    CompanyCredentials getByApiKeyCompanyCredentialsWithStatusActive(String apiKey);

    void updateStatus(String apiKey, GlobalStatuses globalStatus);

    void recordUsageAsync(String credentialsId, boolean success);
}
