package io.javaoperatorsdk.operator.sample.cacheprune;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.util.Lists;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.FieldsV1Builder;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntryBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(itemStore = LabelRemovingItemStore.class)
public class CachePruneReconciler
    implements Reconciler<CachePruneCustomResource>,
    EventSourceInitializer<CachePruneCustomResource>,
    Cleaner<CachePruneCustomResource>, KubernetesClientAware {

  public static final String DATA_KEY = "data";
  public static final String FIELD_MANAGER = "controller";
  public static final String SECONDARY_CREATE_FIELD_MANAGER = "creator";
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
        var patchContext = patchContextWithFieldManager(FIELD_MANAGER);
        // setting new field manager since we don't control label anymore
        patchContext.setForce(true);
        patchContext.setFieldManager(FIELD_MANAGER);
        client.configMaps().resource(cm)
            .patch(patchContext, cloned);
      }
    }, () -> client.configMaps().resource(configMap(resource))
        .patch(patchContextWithFieldManager(SECONDARY_CREATE_FIELD_MANAGER)));

    resource.setStatus(new CachePruneStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  private PatchContext patchContextWithFieldManager(String fieldManager) {
    PatchContext patchContext = new PatchContext();
    patchContext.setFieldManager(fieldManager);
    return patchContext;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<CachePruneCustomResource> context) {
    InformerEventSource<ConfigMap, CachePruneCustomResource> configMapEventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .withItemStore(new LabelRemovingItemStore<>())
            .build(),
            context);
    return EventSourceInitializer.nameEventSources(configMapEventSource);
  }

  ConfigMap configMap(CachePruneCustomResource resource) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(resource.getMetadata().getName());
    configMap.getMetadata().setNamespace(resource.getMetadata().getNamespace());
    configMap.getMetadata().setManagedFields(Lists.list(new ManagedFieldsEntryBuilder()
        .withApiVersion("v1")
        .withFieldsType("FieldsV1")
        .withManager("fabric8")
        .withOperation("Apply")
        .withTime("2022-11-30T09:27:00.000Z")
        .withFieldsV1(new FieldsV1Builder()
            .withAdditionalProperties(Map.of("f:data", Map.of("f:data", Map.of())))
            .build())
        .build()));
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
