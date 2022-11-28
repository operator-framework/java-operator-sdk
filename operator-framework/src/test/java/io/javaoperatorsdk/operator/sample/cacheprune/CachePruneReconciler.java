package io.javaoperatorsdk.operator.sample.cacheprune;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@ControllerConfiguration
public class CachePruneReconciler
    implements Reconciler<CachePruneCustomResource>, EventSourceInitializer<CachePruneCustomResource>,
        Cleaner<CachePruneCustomResource>, KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<CachePruneCustomResource> reconcile(
      CachePruneCustomResource resource,
      Context<CachePruneCustomResource> context) {
    numberOfExecutions.addAndGet(1);




    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<CachePruneCustomResource> context) {

    InformerEventSource<ConfigMap, CachePruneCustomResource> configMapEventSource =
        new InformerEventSource<ConfigMap, CachePruneCustomResource>(InformerConfiguration.from(ConfigMap.class,context).build(),
                context);
    return EventSourceInitializer.nameEventSources(configMapEventSource);
  }

  ConfigMap configMap(String name, CachePruneCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(name);
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.setData(new HashMap<>());
    configMap.getData().put(name, name);
    HashMap<String, String> labels = new HashMap<>();
    labels.put("multisecondary", "true");
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
  public DeleteControl cleanup(CachePruneCustomResource resource, Context<CachePruneCustomResource> context) {
    return null;
  }
}
