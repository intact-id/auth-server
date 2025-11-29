package com.intact_id.service.apiKey;

import com.intact_id.dto.response.ApiKeyGenerationResponseDto;

import java.util.List;

public interface ApiKeyService {
    ApiKeyGenerationResponseDto generateApiKey(String companyId);

    void revokeApiKey(String companyId, String apiKeyId);

    List<ApiKeyGenerationResponseDto> getApiKeys(String companyId);
}
