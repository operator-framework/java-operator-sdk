package io.javaoperatorsdk.operator.sample.informereventsource;

import java.util.Arrays;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.InformerEventSource;

import static io.javaoperatorsdk.operator.api.Controller.NO_FINALIZER;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@Controller(finalizerName = NO_FINALIZER)
public class InformerEventSourceTestCustomResourceController implements
    ResourceController<InformerEventSourceTestCustomResource>, KubernetesClientAware {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(InformerEventSourceTestCustomResourceController.class);

  public static final String RELATED_RESOURCE_UID = "relatedResourceUID";
  public static final String TARGET_CONFIG_MAP_KEY = "targetStatus";

  private KubernetesClient kubernetesClient;
  private SharedInformer<ConfigMap> informer;

  @Override
  public void init(EventSourceManager eventSourceManager) {
    SharedInformerFactory sharedInformerFactory = kubernetesClient.informers();
    informer = sharedInformerFactory.sharedIndexInformerFor(ConfigMap.class, 0);
    eventSourceManager.registerEventSource("configmap", new InformerEventSource<>(informer,
        resource -> {
          if (resource.getMetadata() == null || resource.getMetadata().getAnnotations() == null) {
            return Collections.emptyList();
          }
          return Arrays.asList(resource.getMetadata().getAnnotations().get(RELATED_RESOURCE_UID));
        }));
  }

  @Override
  public UpdateControl<InformerEventSourceTestCustomResource> createOrUpdateResource(
      InformerEventSourceTestCustomResource resource,
      Context<InformerEventSourceTestCustomResource> context) {

    // Reading the config map from the informer not from the API
    // name of the config map same as custom resource for sake of simplicity
    ConfigMap configMap =
        informer.getStore().getByKey(Cache.namespaceKeyFunc(resource.getMetadata().getNamespace(),
            resource.getMetadata().getName()));

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
