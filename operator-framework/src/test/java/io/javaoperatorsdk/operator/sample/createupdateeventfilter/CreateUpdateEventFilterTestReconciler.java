package io.javaoperatorsdk.operator.sample.createupdateeventfilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class CreateUpdateEventFilterTestReconciler
    implements Reconciler<CreateUpdateEventFilterTestCustomResource>,
    EventSourceInitializer<CreateUpdateEventFilterTestCustomResource>,
    KubernetesClientAware {

  public static final String CONFIG_MAP_TEST_DATA_KEY = "key";
  private KubernetesClient client;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private InformerEventSource<ConfigMap, CreateUpdateEventFilterTestCustomResource> informerEventSource;

  @Override
  public UpdateControl<CreateUpdateEventFilterTestCustomResource> reconcile(
      CreateUpdateEventFilterTestCustomResource resource,
      Context<CreateUpdateEventFilterTestCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    ConfigMap configMap =
        client
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName())
            .get();
    if (configMap == null) {
      var configMapToCreate = createConfigMap(resource);
      final var resourceID = ResourceID.fromResource(configMapToCreate);
      try {
        informerEventSource.prepareForCreateOrUpdateEventFiltering(resourceID, configMapToCreate);
        configMap =
            client
                .configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(configMapToCreate);
        informerEventSource.handleRecentResourceCreate(resourceID, configMap);
      } catch (RuntimeException e) {
        informerEventSource
            .cleanupOnCreateOrUpdateEventFiltering(resourceID, configMapToCreate);
        throw e;
      }
    } else {
      ResourceID resourceID = ResourceID.fromResource(configMap);
      if (!Objects.equals(
          configMap.getData().get(CONFIG_MAP_TEST_DATA_KEY), resource.getSpec().getValue())) {
        configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
        try {
          informerEventSource
              .prepareForCreateOrUpdateEventFiltering(resourceID, configMap);
          var newConfigMap =
              client
                  .configMaps()
                  .inNamespace(resource.getMetadata().getNamespace())
                  .replace(configMap);
          informerEventSource.handleRecentResourceUpdate(resourceID,
              newConfigMap, configMap);
        } catch (RuntimeException e) {
          informerEventSource
              .cleanupOnCreateOrUpdateEventFiltering(resourceID, configMap);
          throw e;
        }
      }
    }
    return UpdateControl.noUpdate();
  }

  private ConfigMap createConfigMap(CreateUpdateEventFilterTestCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(resource.getMetadata().getName());
    configMap.getMetadata().setLabels(new HashMap<>());
    configMap.getMetadata().getLabels().put("integrationtest", this.getClass().getSimpleName());
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.setData(new HashMap<>());
    configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
    configMap.addOwnerReference(resource);

    return configMap;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<CreateUpdateEventFilterTestCustomResource> context) {
    InformerConfiguration<ConfigMap, CreateUpdateEventFilterTestCustomResource> informerConfiguration =
        InformerConfiguration.from(context, ConfigMap.class)
            .withLabelSelector("integrationtest = " + this.getClass().getSimpleName())
            .build();
    informerEventSource = new InformerEventSource<>(informerConfiguration, client);
    return Map.of("test-informer", informerEventSource);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return client;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.client = kubernetesClient;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
