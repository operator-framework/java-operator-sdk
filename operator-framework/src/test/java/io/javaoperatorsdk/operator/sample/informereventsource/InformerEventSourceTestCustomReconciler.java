package io.javaoperatorsdk.operator.sample.informereventsource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.NO_FINALIZER;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class InformerEventSourceTestCustomReconciler implements
    Reconciler<InformerEventSourceTestCustomResource>, KubernetesClientAware,
    EventSourceInitializer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(InformerEventSourceTestCustomReconciler.class);

  public static final String RELATED_RESOURCE_NAME = "relatedResourceName";
  public static final String TARGET_CONFIG_MAP_KEY = "targetStatus";

  private KubernetesClient kubernetesClient;
  private InformerEventSource<ConfigMap> eventSource;

  @Override
  public void prepareEventSources(EventSourceRegistry eventSourceRegistry) {
    eventSource = new InformerEventSource<>(kubernetesClient, ConfigMap.class,
        Mappers.fromAnnotation(RELATED_RESOURCE_NAME));
    eventSourceRegistry.registerEventSource(eventSource);
  }

  @Override
  public UpdateControl<InformerEventSourceTestCustomResource> reconcile(
      InformerEventSourceTestCustomResource resource,
      Context context) {

    // Reading the config map from the informer not from the API
    // name of the config map same as custom resource for sake of simplicity
    ConfigMap configMap = eventSource.getAssociated(resource);

    String targetStatus = configMap.getData().get(TARGET_CONFIG_MAP_KEY);
    LOGGER.debug("Setting target status for CR: {}", targetStatus);
    resource.setStatus(new InformerEventSourceTestCustomResourceStatus());
    resource.getStatus().setConfigMapValue(targetStatus);
    return UpdateControl.updateStatus(resource);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }
}
