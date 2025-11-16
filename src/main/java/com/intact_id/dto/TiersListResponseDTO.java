package com.intact_id.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 21:10
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TiersListResponseDTO {
    private List<TierResponseDTO> tiers;
    private Integer totalTiers;
}
