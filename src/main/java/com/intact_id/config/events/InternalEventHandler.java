package com.intact_id.config.events;

import java.lang.reflect.ParameterizedType;

/**
 * Interface for handling internal events with type safety
 */
public interface InternalEventHandler<E extends InternalEvent<?>> {

    /**
     * Handle the event
     */
    void handle(E internalEvent);

    /**
     * Get the generic type class for this handler
     */
    @SuppressWarnings("unchecked")
    default Class<E> getGenericTypeClass() {
        try {
            String className = ((ParameterizedType) getClass().getGenericInterfaces()[0])
                    .getActualTypeArguments()[0].getTypeName();
            Class<?> clazz = Class.forName(className);
            return (Class<E>) clazz;
        } catch (Exception e) {
            throw new IllegalStateException("Class is not parametrized with an internal event!", e);
        }
    }

    /**
     * Validate and publish the event
     */
    default void validateAndPublish(InternalEvent<?> event) {
        var type = getGenericTypeClass();
        handle(type.cast(event));
    }
}
