package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

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

    Class<? extends CustomResourceDoneable<? extends CustomResource>> customResourceDoneableClass() default EMPTY_DONEABLE.class;

    String finalizerName() default DEFAULT_FINALIZER;

    /**
     * If true, will dispatch new event to the controller if generation increased since the last processing, otherwise will
     * process all events.
     * See generation meta attribute
     * <a href="https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#status-subresource">here</a>
     */
    boolean generationAwareEventProcessing() default true;

    static final class EMPTY_DONEABLE extends CustomResourceDoneable<CustomResource> {
        public EMPTY_DONEABLE(CustomResource resource, Function function) {
            super(resource, function);
        }
    }
}
