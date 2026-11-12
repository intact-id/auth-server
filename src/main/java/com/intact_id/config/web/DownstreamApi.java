package com.intact_id.config.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks controllers that front another Intact service; their errors go through DownstreamApiExceptionHandler. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DownstreamApi {

    /** Name shown to callers when the service can't be reached, e.g. "Billing". */
    String value();
}
