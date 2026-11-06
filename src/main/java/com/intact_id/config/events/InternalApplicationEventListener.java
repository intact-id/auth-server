package com.intact_id.config.events;

import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Listener for internal application events
 * Routes events to appropriate handlers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalApplicationEventListener {

    private final List<InternalEventHandler<?>> internalEventHandlers;

    // Off the publishing thread, as before (the app used to make every event async)
    @Async
    @EventListener(InternalEvent.class)
    public void handle(InternalEvent<?> event) {
        internalEventHandlers.stream()
                .filter(handler -> handler.getGenericTypeClass().equals(event.getClass()))
                .forEach(handler -> {
                    try {
                        handler.validateAndPublish(event);
                    } catch (Exception e) {
                        log.error("Error handling event {}: {}",
                                event.getClass().getSimpleName(), e.getMessage(), e);
                    }
                });
    }
}
