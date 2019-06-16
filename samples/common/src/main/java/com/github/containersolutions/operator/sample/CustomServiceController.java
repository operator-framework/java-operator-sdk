package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;

import java.util.Arrays;
import java.util.Optional;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class,
        kind = CustomServiceController.CRD_NAME,
        group = CustomServiceController.GROUP,
        customResourceListClass = CustomServiceList.class,
        customResourceDonebaleClass = CustomServiceDoneable.class)
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String CRD_NAME = "CustomService";
    public static final String GROUP = "sample.javaoperatorsdk";

    @Override
    public void deleteResource(CustomService resource, Context<CustomService> context) {
        context.getK8sClient().services().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName()).delete();
    }

    @Override
    public Optional<CustomService> createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        ServicePort servicePort = new ServicePort();
        servicePort.setPort(8080);
        ServiceSpec serviceSpec = new ServiceSpec();
        serviceSpec.setPorts(Arrays.asList(servicePort));

        context.getK8sClient().services().inNamespace(resource.getMetadata().getNamespace()).createOrReplaceWithNew()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .withSpec(serviceSpec)
                .done();
        return Optional.of(resource);
    }
}
