package io.javaoperatorsdk.operator.sample;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

/** A very simple sample controller that creates a service with a label. */
@Controller
public class CustomServiceController implements ResourceController<CustomService> {

  public static final String KIND = "CustomService";
  private static final Logger log = LoggerFactory.getLogger(CustomServiceController.class);

  private final KubernetesClient kubernetesClient;

  public CustomServiceController() {
    this(new DefaultKubernetesClient());
  }

  public CustomServiceController(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public DeleteControl deleteResource(CustomService resource, Context context) {
    log.info("Execution deleteResource for: {}", resource.getMetadata().getName());
    return DeleteControl.defaultDelete();
  }

  @Override
  public UpdateControl<CustomService> createOrUpdateResource(
      CustomService resource, Context context) {
    log.info("Execution createOrUpdateResource for: {}", resource.getMetadata().getName());

    ServicePort servicePort = new ServicePort();
    servicePort.setPort(8080);
    ServiceSpec serviceSpec = new ServiceSpec();
    serviceSpec.setPorts(Collections.singletonList(servicePort));

    kubernetesClient
        .services()
        .inNamespace(resource.getMetadata().getNamespace())
        .createOrReplace(
            new ServiceBuilder()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .withSpec(serviceSpec)
                .build());
    return UpdateControl.updateCustomResource(resource);
  }
}
