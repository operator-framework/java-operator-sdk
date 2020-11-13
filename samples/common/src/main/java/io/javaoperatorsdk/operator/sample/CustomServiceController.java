package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class,
        crdName = "customservices.sample.javaoperatorsdk")
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String KIND = "CustomService";
    private final static Logger log = LoggerFactory.getLogger(CustomServiceController.class);

    private final KubernetesClient kubernetesClient;

    public CustomServiceController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public DeleteControl deleteResource(CustomService resource, Context<CustomService> context) {
        log.info("Execution deleteResource for: {}", resource.getMetadata().getName());
        kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName()).delete();
        return new DeleteControl();
    }

    @Override
    public UpdateControl<CustomService> createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        log.info("Execution createOrUpdateResource for: {}", resource.getMetadata().getName());

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(8080);
        ServiceSpec serviceSpec = new ServiceSpec();
        serviceSpec.setPorts(Collections.singletonList(servicePort));

        kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace()).createOrReplaceWithNew()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .withSpec(serviceSpec)
                .done();
        return UpdateControl.updateCustomResource(resource);
    }
}
