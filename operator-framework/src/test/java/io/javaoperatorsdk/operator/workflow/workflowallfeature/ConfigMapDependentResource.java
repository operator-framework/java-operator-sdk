package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ConfigMapDependentResource
    extends KubernetesDependentResource<ConfigMap, WorkflowAllFeatureCustomResource>
    implements Creator<ConfigMap, WorkflowAllFeatureCustomResource>,
        Updater<ConfigMap, WorkflowAllFeatureCustomResource>,
        Deleter<WorkflowAllFeatureCustomResource> {

  public static final String READY_TO_DELETE_ANNOTATION = "ready-to-delete";

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

  @Override
  protected ConfigMap desired(
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of("key", "data"));
    return configMap;
  }

  @Override
  public void delete(
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    Optional<ConfigMap> optionalConfigMap = context.getSecondaryResource(ConfigMap.class);
    if (optionalConfigMap.isEmpty()) {
      log.debug("Config Map not found for primary: {}", ResourceID.fromResource(primary));
      return;
    }
    optionalConfigMap.ifPresent(
        (configMap -> {
          if (configMap.getMetadata().getAnnotations() != null
              && configMap.getMetadata().getAnnotations().get(READY_TO_DELETE_ANNOTATION) != null) {
            context.getClient().resource(configMap).delete();
          }
        }));
  }
}
