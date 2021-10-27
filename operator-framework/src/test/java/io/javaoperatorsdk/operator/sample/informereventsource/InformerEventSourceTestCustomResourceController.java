package io.javaoperatorsdk.operator.sample.informereventsource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.internal.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javaoperatorsdk.operator.api.Controller.NO_FINALIZER;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@Controller(finalizerName = NO_FINALIZER)
public class InformerEventSourceTestCustomResourceController implements
    ResourceController<InformerEventSourceTestCustomResource>, KubernetesClientAware,
    EventSourceInitializer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(InformerEventSourceTestCustomResourceController.class);

  public static final String RELATED_RESOURCE_UID = "relatedResourceName";
  public static final String TARGET_CONFIG_MAP_KEY = "targetStatus";

  private KubernetesClient kubernetesClient;
  private InformerEventSource<ConfigMap> eventSource;

  @Override
  public void prepareEventSources(EventSourceManager eventSourceManager) {
    eventSource = new InformerEventSource<>(kubernetesClient, ConfigMap.class,
        Mappers.fromAnnotation(RELATED_RESOURCE_UID));
    eventSourceManager.registerEventSource(eventSource);
  }

  @Override
  public UpdateControl<InformerEventSourceTestCustomResource> createOrUpdateResource(
          InformerEventSourceTestCustomResource resource,
          Context context) {

    // Reading the config map from the informer not from the API
    // name of the config map same as custom resource for sake of simplicity
    ConfigMap configMap = eventSource.getAssociated(resource);

    String targetStatus = configMap.getData().get(TARGET_CONFIG_MAP_KEY);
    LOGGER.debug("Setting target status for CR: {}", targetStatus);
    resource.setStatus(new InformerEventSourceTestCustomResourceStatus());
    resource.getStatus().setConfigMapValue(targetStatus);
    return UpdateControl.updateStatusSubResource(resource);
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
