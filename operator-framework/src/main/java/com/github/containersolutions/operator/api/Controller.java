package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Controller {

    String DEFAULT_FINALIZER = "operator.default.finalizer";

    String DEFAULT_VERSION = "v1";

    String version() default DEFAULT_VERSION;

    String group();

    String kind();

    Class<? extends CustomResource> customResourceClass();

    String defaultFinalizer() default DEFAULT_FINALIZER;

}
