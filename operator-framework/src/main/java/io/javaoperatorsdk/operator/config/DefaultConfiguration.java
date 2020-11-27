
package io.javaoperatorsdk.operator.config;

import java.util.Locale;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;

public class DefaultConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {
    private final ResourceController<R> controller;
    private final Controller annotation;
    
    public DefaultConfiguration(ResourceController<R> controller) {
        this.controller = controller;
        this.annotation = controller.getClass().getAnnotation(Controller.class);
    }
    
    @Override
    public String getName() {
        final var name = annotation.name();
        return Controller.NULL.equals(name) ? controller.getClass().getSimpleName().toLowerCase(Locale.ROOT) : name;
    }
    
    @Override
    public String getCRDName() {
        return annotation.crdName();
    }
    
    @Override
    public String getFinalizer() {
        final String annotationFinalizerName = annotation.finalizerName();
        if (!Controller.NULL.equals(annotationFinalizerName)) {
            return annotationFinalizerName;
        }
        return ControllerUtils.getDefaultFinalizerName(annotation.crdName());
    }
    
    @Override
    public boolean isGenerationAware() {
        return annotation.generationAwareEventProcessing();
    }
    
    @Override
    public Class<R> getCustomResourceClass() {
        return ControllerUtils.getCustomResourceClass(controller);
    }
}
