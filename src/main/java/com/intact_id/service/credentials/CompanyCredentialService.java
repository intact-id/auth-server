package com.intact_id.service.credentials;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyCredentials;
import org.springframework.stereotype.Service;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 23/11/2025 14:23
 */

@Service
public interface CompanyCredentialService {
    CompanyCredentials getByApiKeyCompanyCredentials(String apiKey, ApiKeyEnvironment environment);
    CompanyCredentials getByApiKeyCompanyCredentialsWithStatusActive(String apiKey, ApiKeyEnvironment environment);
    void updateStatus(String apiKey, ApiKeyEnvironment environment, GlobalStatuses globalStatus);
    void recordUsageAsync(String credentialsId, ApiKeyEnvironment environment, boolean success);
}
