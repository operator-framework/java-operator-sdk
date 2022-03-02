package io.javaoperatorsdk.operator.sample.createupdateeventfilter;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
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
      CreateUpdateEventFilterTestCustomResource resource, Context context) {
    numberOfExecutions.incrementAndGet();

    ConfigMap configMap =
        client
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName())
            .get();
    if (configMap == null) {
      var configMapToCreate = createConfigMap(resource);
      try {
        informerEventSource.prepareForCreateOrUpdateEventFiltering(configMapToCreate);
        configMap =
            client
                .configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .create(configMapToCreate);
        informerEventSource.handleRecentResourceCreate(configMap);
      } catch (RuntimeException e) {
        informerEventSource
            .cleanupOnCreateOrUpdateEventFiltering(configMapToCreate);
        throw e;
      }
    } else {
      if (!Objects.equals(
          configMap.getData().get(CONFIG_MAP_TEST_DATA_KEY), resource.getSpec().getValue())) {
        configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
        try {
          informerEventSource
              .prepareForCreateOrUpdateEventFiltering(configMap);
          var newConfigMap =
              client
                  .configMaps()
                  .inNamespace(resource.getMetadata().getNamespace())
                  .replace(configMap);
          informerEventSource.handleRecentResourceUpdate(
              newConfigMap, configMap.getMetadata().getResourceVersion());
        } catch (RuntimeException e) {
          informerEventSource
              .cleanupOnCreateOrUpdateEventFiltering(configMap);
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
  public List<EventSource> prepareEventSources(
      EventSourceContext<CreateUpdateEventFilterTestCustomResource> context) {
    InformerConfiguration<ConfigMap, CreateUpdateEventFilterTestCustomResource> informerConfiguration =
        InformerConfiguration.from(context, ConfigMap.class)
            .withLabelSelector("integrationtest = " + this.getClass().getSimpleName())
            .build();
    informerEventSource = new InformerEventSource<>(informerConfiguration, client);
    return List.of(informerEventSource);
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
