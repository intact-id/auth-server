package com.intact_id.service.kyc.events;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RateLimitConsumedEvent extends ApplicationEvent {

    private final String companyId;
    private final ApiKeyEnvironment environment;

    public RateLimitConsumedEvent(Object source, String companyId, ApiKeyEnvironment environment) {
        super(source);
        this.companyId = companyId;
        this.environment = environment;
    }
}
