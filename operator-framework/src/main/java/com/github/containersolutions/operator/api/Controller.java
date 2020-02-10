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

    String crdName();

    Class<? extends CustomResource> customResourceClass();

    Class<? extends CustomResourceDoneable> customResourceDoneableClass() default CustomResourceDoneable.class;

    Class<? extends CustomResourceList> customResourceListClass() default CustomResourceList.class;

    String finalizerName() default DEFAULT_FINALIZER;
}
