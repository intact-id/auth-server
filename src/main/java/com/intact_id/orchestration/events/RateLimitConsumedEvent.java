package com.intact_id.orchestration.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RateLimitConsumedEvent extends ApplicationEvent {

    private final String companyId;

    public RateLimitConsumedEvent(Object source, String companyId) {
        super(source);
        this.companyId = companyId;
    }
}
