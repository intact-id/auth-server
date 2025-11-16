package com.intact_id.util.mapper;

import com.intact_id.config.TiersConfig;
import com.intact_id.dto.TierResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 21:25
 */
@Service
public class TiersResponseMappersUtil {
    public TierResponseDTO mapToDTO(String tierKey, TiersConfig.Tier tier) {
        return TierResponseDTO.builder()
                .tierKey(tierKey)
                .name(tier.getName())
                .priceRange(tier.getPriceRange())
                .features(tier.getFeatures())
                .exclusions(tier.getExclusions() != null ? tier.getExclusions() : List.of())
                .build();
    }
}
