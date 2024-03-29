package io.javaoperatorsdk.operator.sample.createupdateeventfilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class CreateUpdateEventFilterTestReconciler
    implements Reconciler<CreateUpdateEventFilterTestCustomResource>,
    EventSourceInitializer<CreateUpdateEventFilterTestCustomResource> {

  private static final class DirectConfigMapDependentResource
      extends
      CRUDKubernetesDependentResource<ConfigMap, CreateUpdateEventFilterTestCustomResource> {

    private ConfigMap desired;

    private DirectConfigMapDependentResource(Class<ConfigMap> resourceType) {
      super(resourceType);
    }

    @Override
    protected ConfigMap desired(CreateUpdateEventFilterTestCustomResource primary,
        Context<CreateUpdateEventFilterTestCustomResource> context) {
      return desired;
    }

    @Override
    public void setEventSource(
        io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource<ConfigMap, CreateUpdateEventFilterTestCustomResource> eventSource) {
      super.setEventSource(eventSource);
    }
  }

  public static final String CONFIG_MAP_TEST_DATA_KEY = "key";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private InformerEventSource<ConfigMap, CreateUpdateEventFilterTestCustomResource> informerEventSource;
  private DirectConfigMapDependentResource configMapDR =
      new DirectConfigMapDependentResource(ConfigMap.class);

  @Override
  public UpdateControl<CreateUpdateEventFilterTestCustomResource> reconcile(
      CreateUpdateEventFilterTestCustomResource resource,
      Context<CreateUpdateEventFilterTestCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    ConfigMap configMap =
        context.getClient()
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName())
            .get();
    if (configMap == null) {
      configMapDR.desired = createConfigMap(resource);
      configMapDR.reconcile(resource, context);
    } else {
      if (!Objects.equals(
          configMap.getData().get(CONFIG_MAP_TEST_DATA_KEY), resource.getSpec().getValue())) {
        configMap.getData().put(CONFIG_MAP_TEST_DATA_KEY, resource.getSpec().getValue());
        configMapDR.desired = configMap;
        configMapDR.reconcile(resource, context);
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
    InformerConfiguration<ConfigMap> informerConfiguration =
        InformerConfiguration.from(ConfigMap.class)
            .withLabelSelector("integrationtest = " + this.getClass().getSimpleName())
            .build();
    informerEventSource = new InformerEventSource<>(informerConfiguration, context.getClient());
    this.configMapDR.setEventSource(informerEventSource);

    return EventSourceInitializer.nameEventSources(informerEventSource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
