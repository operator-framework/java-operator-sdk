package io.javaoperatorsdk.operator.sample;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;

/** A very simple sample controller that creates a service with a label. */
@ControllerConfiguration
public class CustomServiceReconciler implements Reconciler<CustomService> {

  private static final Logger log = LoggerFactory.getLogger(CustomServiceReconciler.class);

  private final KubernetesClient kubernetesClient;

  public CustomServiceReconciler() {
    this(new DefaultKubernetesClient());
  }

  public CustomServiceReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public DeleteControl cleanup(CustomService resource, Context context) {
    log.info("Cleaning up for: {}", resource.getMetadata().getName());
    return Reconciler.super.cleanup(resource, context);
  }

  @Override
  public UpdateControl<CustomService> reconcile(
      CustomService resource, Context context) {
    log.info("Reconciling: {}", resource.getMetadata().getName());

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
    return UpdateControl.updateResource(resource);
  }
}
