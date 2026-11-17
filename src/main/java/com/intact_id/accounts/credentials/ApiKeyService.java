package com.intact_id.accounts.credentials;

import com.intact_id.accounts.web.ApiKeyGenerationResponseDto;
import com.intact_id.accounts.web.ApiKeyUsageResponseDto;

import java.util.List;

public interface ApiKeyService {
    ApiKeyGenerationResponseDto generateApiKey(String companyId);
    ApiKeyUsageResponseDto getUsage(String companyId);

    void revokeApiKey(String companyId, String apiKeyId);

    List<ApiKeyGenerationResponseDto> getApiKeys(String companyId);

    /** What the company's API key may do from now on (FULL, VERIFY or READ_ONLY). Tokens already issued keep theirs until they expire. */
    String setAccess(String companyId, com.intact_id.accounts.Permissions.ApiAccess access);
}
