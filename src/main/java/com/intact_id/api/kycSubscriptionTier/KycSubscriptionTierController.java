
package com.intact_id.api.kycSubscriptionTier;

import com.intact_id.config.TiersConfig;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.TierResponseDTO;
import com.intact_id.dto.response.TiersListResponseDTO;
import com.intact_id.util.mapper.MapperUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tiers")
@Tag(name = "KYC Subscription Tiers", description = "Endpoints for managing KYC subscription tiers and pricing")
public class KycSubscriptionTierController {

    private final TiersConfig tiersConfig;
    private final MapperUtils mapperUtils;

    @GetMapping
    @Operation(
            summary = "Get all KYC subscription tiers",
            description = "Retrieves all available KYC verification subscription tiers with pricing and features"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved tiers",
                    content = @Content(schema = @Schema(implementation = TiersListResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    public ResponseEntity<ApiResponse<TiersListResponseDTO>> getAllTiers() {
        try {
            log.info("Fetching all KYC subscription tiers");

            List<TierResponseDTO> tierList = tiersConfig.getTiers()
                    .entrySet()
                    .stream()
                    .map(entry -> mapperUtils.mapTiersToDTO(entry.getKey(), entry.getValue()))
                    .toList();

            log.info("Successfully fetched {} tiers", tierList.size());

            TiersListResponseDTO response = TiersListResponseDTO.builder()
                    .tiers(tierList)
                    .build();

            return ResponseEntity.ok(
                    ApiResponse.<TiersListResponseDTO>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Fetched tiers successfully")
                            .success(true)
                            .data(response)
                            .build()
            );

        } catch (Exception e) {
            log.error("Error fetching tiers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            ApiResponse.<TiersListResponseDTO>builder()
                                    .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                    .responseMessage("Failed to fetch tiers: " + e.getMessage())
                                    .success(false)
                                    .data(null)
                                    .build()
                    );
        }
    }

    @GetMapping("/{tierKey}")
    @Operation(
            summary = "Get specific tier by key",
            description = "Retrieves details of a specific KYC tier by its key (TIER_1, TIER_2, or TIER_3)"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved tier",
                    content = @Content(schema = @Schema(implementation = TierResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Tier not found"
            )
    })
    public ResponseEntity<ApiResponse<TierResponseDTO>> getTierByKey(@PathVariable String tierKey) {
        try {
            log.info("Fetching tier with key: {}", tierKey);

            TiersConfig.Tier tier = tiersConfig.getTier(tierKey);

            if (tier == null) {
                log.warn("Tier not found: {}", tierKey);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.<TierResponseDTO>builder()
                                .responseCode(HttpStatus.NOT_FOUND.value())
                                .responseMessage("Tier not found: " + tierKey)
                                .success(false)
                                .data(null)
                                .build());
            }

            TierResponseDTO tierDTO = mapperUtils.mapTiersToDTO(tierKey, tier);

            return ResponseEntity.ok(
                    ApiResponse.<TierResponseDTO>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Tier fetched successfully")
                            .success(true)
                            .data(tierDTO)
                            .build()
            );

        } catch (Exception e) {
            log.error("Error fetching tier: {}", tierKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<TierResponseDTO>builder()
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Failed to fetch tier: " + e.getMessage())
                            .success(false)
                            .data(null)
                            .build());
        }
    }
}