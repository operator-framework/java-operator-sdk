package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class,
        kind = CustomServiceController.CRD_NAME)
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String CRD_NAME = "CustomService";

    @Override
    public void deleteResource(CustomService resource, Context<CustomService> context) {
        context.getK8sClient().services().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public CustomService createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        context.getK8sClient().services().inNamespace(resource.getMetadata().getNamespace()).createOrReplaceWithNew()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .done();
        return resource;
    }
}
