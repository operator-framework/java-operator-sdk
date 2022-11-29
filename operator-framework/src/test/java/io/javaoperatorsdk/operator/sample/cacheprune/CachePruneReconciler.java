package io.javaoperatorsdk.operator.sample.cacheprune;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
// @ControllerConfiguration(itemStore = LabelRemovingItemStore.class)
public class CachePruneReconciler
    implements Reconciler<CachePruneCustomResource>,
    EventSourceInitializer<CachePruneCustomResource>,
    Cleaner<CachePruneCustomResource>, KubernetesClientAware {

  public static final String DATA_KEY = "data";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<CachePruneCustomResource> reconcile(
      CachePruneCustomResource resource,
      Context<CachePruneCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var configMap = context.getSecondaryResource(ConfigMap.class);
    configMap.ifPresentOrElse(cm -> {
      if (!cm.getData().get(DATA_KEY)
          .equals(resource.getSpec().getData())) {
        var cloned = ConfigurationServiceProvider.instance().getResourceCloner().clone(cm);
        cloned.getData().put(DATA_KEY, resource.getSpec().getData());
        var res = client.configMaps().resource(cm)
            .patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), cloned);
        System.out.println(res);
      }
    }, () -> client.configMaps().resource(configMap(resource)).create());

    resource.setStatus(new CachePruneStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<CachePruneCustomResource> context) {
    InformerEventSource<ConfigMap, CachePruneCustomResource> configMapEventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            // .withItemStore(new LabelRemovingItemStore<>())
            .build(),
            context);
    return EventSourceInitializer.nameEventSources(configMapEventSource);
  }

  ConfigMap configMap(CachePruneCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(resource.getMetadata().getName());
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.setData(Map.of(DATA_KEY, resource.getSpec().getData()));
    HashMap<String, String> labels = new HashMap<>();
    labels.put("mylabel", "val");
    configMap.getMetadata().setLabels(labels);
    configMap.addOwnerReference(resource);
    return configMap;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  @Override
  public DeleteControl cleanup(CachePruneCustomResource resource,
      Context<CachePruneCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
