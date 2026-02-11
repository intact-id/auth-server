package com.intact_id.service.apiKey;

import com.intact_id.dto.response.ApiKeyGenerationResponseDto;
import com.intact_id.dto.response.ApiKeyUsageResponseDto;

import java.util.List;

public interface ApiKeyService {
    ApiKeyGenerationResponseDto generateApiKey(String companyId);
    ApiKeyGenerationResponseDto generateDevApiKey(String companyId);
    ApiKeyGenerationResponseDto generateProdApiKey(String companyId);
    ApiKeyUsageResponseDto getUsage(String companyId);

    void revokeApiKey(String companyId, String apiKeyId);

    List<ApiKeyGenerationResponseDto> getApiKeys(String companyId);
}
