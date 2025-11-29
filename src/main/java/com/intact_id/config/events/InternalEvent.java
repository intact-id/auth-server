package com.intact_id.config.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

/**
 * Base class for internal application events
 */
@Getter
public abstract class InternalEvent<D extends Serializable> extends ApplicationEvent {

    private final D data;

    protected InternalEvent(Object source, D data) {
        super(source);
        this.data = data;
    }
}
