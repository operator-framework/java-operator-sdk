package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

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

    Class<? extends CustomResourceList<? extends CustomResource>> customResourceListClass();

    Class<? extends CustomResourceDoneable<? extends CustomResource>> customResourceDonebaleClass();

    String defaultFinalizer() default DEFAULT_FINALIZER;

}
