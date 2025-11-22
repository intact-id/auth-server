package com.intact_id.util.mapper;

import com.intact_id.config.TiersConfig;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.model.CompanyApplicationApproval;
import com.intact_id.dto.response.ApplicationApprovalResponseDto;
import com.intact_id.dto.response.CompanyApplicationResponseDto;
import com.intact_id.dto.response.TierResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 21:25
 */
@Service
public class MapperUtils {
    public TierResponseDTO mapTiersToDTO(String tierKey, TiersConfig.Tier tier) {
        return TierResponseDTO.builder()
                .tierKey(tierKey)
                .name(tier.getName())
                .priceRange(tier.getPriceRange())
                .features(tier.getFeatures())
                .exclusions(tier.getExclusions() != null ? tier.getExclusions() : List.of())
                .build();
    }


    public CompanyApplicationResponseDto toApplicationResponseDto(CompanyApplication entity) {
        return CompanyApplicationResponseDto.builder()
                .id(entity.getId())
                .applicationNumber(entity.getApplicationNumber())
                .legalName(entity.getLegalName())
                .tradingName(entity.getTradingName())
                .registrationNumber(entity.getRegistrationNumber())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .website(entity.getWebsite())
                .country(entity.getCountry())
                .applicationStatus(entity.getApplicationStatus())
                .businessType(entity.getBusinessType())
                .contactPersonName(entity.getContactPersonName())
                .contactPersonTitle(entity.getContactPersonTitle())
                .contactPersonEmail(entity.getContactPersonEmail())
                .contactPersonPhone(entity.getContactPersonPhone())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    public ApplicationApprovalResponseDto toApplicationApprovalResponseDto(CompanyApplicationApproval approval) {
        if (approval == null) {
            return null;
        }

        ApplicationApprovalResponseDto dto = new ApplicationApprovalResponseDto();

        dto.setId(approval.getId());
        dto.setCompanyId(approval.getCompanyId());
        dto.setApplicationId(approval.getApplicationId());
        dto.setPerformedBy(approval.getPerformedBy());
        dto.setPerformedByAction(approval.getPerformedByAction());
        dto.setApprovedBy(approval.getApprovedBy());
        dto.setFinalAction(approval.getFinalAction());
        dto.setApproverNote(approval.getApproverNote());
        dto.setEndUserMessage(approval.getEndUserMessage());
        dto.setActionPerformedAt(approval.getActionPerformedAt());
        dto.setActionApprovedAt(approval.getActionApprovedAt());
        dto.setCreatedAt(approval.getCreatedAt());
        dto.setUpdatedAt(approval.getUpdatedAt());

        return dto;
    }
}
