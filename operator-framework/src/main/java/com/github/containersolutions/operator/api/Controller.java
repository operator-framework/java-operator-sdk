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
    String DEFAULT_API_EXTENSION_VERSION = "apiextensions.k8s.io/v1beta1";
    String DEFAULT_API_VERSION = "v1";

    String apiVersion() default DEFAULT_API_EXTENSION_VERSION;

    String crdVersion() default DEFAULT_API_VERSION;

    String kind();

    Class<? extends CustomResource> customResourceClass();

    String defaultFinalizer() default DEFAULT_FINALIZER;

}
