package com.intact_id.orchestration.events;

import com.intact_id.config.events.InternalEvent;

public class VerificationStatusChangedEvent extends InternalEvent<VerificationStatusChangedData> {

    public VerificationStatusChangedEvent(Object source, VerificationStatusChangedData data) {
        super(source, data);
    }
}
