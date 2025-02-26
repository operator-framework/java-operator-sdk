package io.javaoperatorsdk.operator.api.reconciler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for downstream tooling to ignore the annotated {@link Reconciler}. This allows to
 * mark some implementations as not provided by user and should therefore be ignored by processes
 * external to the SDK itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Ignore {}
