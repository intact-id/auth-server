package com.intact_id.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 21:11
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierResponseDTO {
    private String tierKey;        // "tier1", "tier2", "tier3"
    private String name;           // "Basic", "Standard KYC", "Enterprise"
    private List<Double> priceRange; // [0.30, 0.40]
    private List<String> features;
    private List<String> exclusions;
}
