package com.intact_id.orchestration.web;

import com.intact_id.orchestration.kyb.PartyInput;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * A business draft's details (companyName, registrationNumber, country, tradingName, incorporationDate,
 * registeredAddress, taxPin, businessType) and its directors and owners. Each part sent replaces what was saved.
 */
@Data
public class DraftBusinessRequestDto {

    private Map<String, String> business;

    @Size(max = 50, message = "Add at most 50 directors and owners")
    private List<PartyInput> parties;
}
