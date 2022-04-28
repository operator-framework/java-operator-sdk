package io.javaoperatorsdk.operator.sample.multiplesecondaryeventsource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

@ControllerConfiguration
public class MultipleSecondaryEventSourceReconciler
    implements Reconciler<MultipleSecondaryEventSourceCustomResource>, TestExecutionInfoProvider,
    EventSourceInitializer<MultipleSecondaryEventSourceCustomResource>, KubernetesClientAware {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<MultipleSecondaryEventSourceCustomResource> reconcile(
      MultipleSecondaryEventSourceCustomResource resource,
      Context<MultipleSecondaryEventSourceCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    if (client.configMaps().inNamespace(resource.getMetadata().getNamespace())
        .withName(getName1(resource)).get() == null) {
      client.configMaps().inNamespace(resource.getMetadata().getNamespace())
          .createOrReplace(configMap(getName1(resource), resource));
    }
    if (client.configMaps().inNamespace(resource.getMetadata().getNamespace())
        .withName(getName2(resource)).get() == null) {
      client.configMaps().inNamespace(resource.getMetadata().getNamespace())
          .createOrReplace(configMap(getName2(resource), resource));
    }

    if (numberOfExecutions.get() >= 3) {
      if (context.getSecondaryResources(ConfigMap.class).size() != 2) {
        throw new IllegalStateException("There should be 2 related config maps");
      }
    }
    return UpdateControl.noUpdate();
  }

  public static String getName1(MultipleSecondaryEventSourceCustomResource resource) {
    return resource.getMetadata().getName() + "1";
  }

  public static String getName2(MultipleSecondaryEventSourceCustomResource resource) {
    return resource.getMetadata().getName() + "2";
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<MultipleSecondaryEventSourceCustomResource> context) {


    var config = InformerConfiguration.from(ConfigMap.class)
        .withNamespaces(context.getControllerConfiguration().getNamespaces())
        .withLabelSelector("multisecondary")
        .withSecondaryToPrimaryMapper(s -> {
          var name =
              s.getMetadata().getName().subSequence(0, s.getMetadata().getName().length() - 1);
          return Set.of(new ResourceID(name.toString(), s.getMetadata().getNamespace()));
        }).build();
    InformerEventSource<ConfigMap, MultipleSecondaryEventSourceCustomResource> configMapEventSource =
        new InformerEventSource<ConfigMap, MultipleSecondaryEventSourceCustomResource>(config,
            context);
    return EventSourceInitializer.nameEventSources(configMapEventSource);
  }

  ConfigMap configMap(String name, MultipleSecondaryEventSourceCustomResource resource) {
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
}
