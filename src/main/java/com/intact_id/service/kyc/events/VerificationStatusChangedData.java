package com.intact_id.service.kyc.events;

import com.intact_id.constant.enums.GlobalStatuses;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

@Getter
public class VerificationStatusChangedData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String verificationId;
    private final String companyId;
    private final GlobalStatuses status;

    public VerificationStatusChangedData(String verificationId, String companyId, GlobalStatuses status) {
        this.verificationId = verificationId;
        this.companyId = companyId;
        this.status = status;
    }
}
