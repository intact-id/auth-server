package com.intact_id.tenancy.credentials;

import com.intact_id.tenancy.web.ApiKeyGenerationResponseDto;
import com.intact_id.tenancy.web.ApiKeyUsageResponseDto;

import java.util.List;

public interface ApiKeyService {
    ApiKeyGenerationResponseDto generateApiKey(String companyId);
    ApiKeyGenerationResponseDto generateDevApiKey(String companyId);
    ApiKeyGenerationResponseDto generateProdApiKey(String companyId);
    ApiKeyUsageResponseDto getUsage(String companyId);

    void revokeApiKey(String companyId, String apiKeyId);

    List<ApiKeyGenerationResponseDto> getApiKeys(String companyId);
}
