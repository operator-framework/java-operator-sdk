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

    String crdName();

    Class<? extends CustomResource> customResourceClass();

    String finalizerName() default DEFAULT_FINALIZER;

}
