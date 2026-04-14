package com.intact_id.service.credentials;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyCredentials;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    /**
     * Generate PROD (live) API key + secret for a company on demand.
     * Returns the plaintext credentials — shown only once; the caller must
     * surface them to the user immediately as they are never stored in plaintext.
     *
     * @param companyId the company's UUID
     * @return map with keys "apiKey" and "apiSecret"
     */
    Map<String, String> generateProdCredentials(String companyId);
}
