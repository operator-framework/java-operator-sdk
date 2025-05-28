package io.javaoperatorsdk.operator.dependent.bulkdependent.readonly;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@KubernetesDependent
public class ReadOnlyBulkDependentResource
    extends KubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements BulkDependentResource<ConfigMap, BulkDependentTestCustomResource>,
        SecondaryToPrimaryMapper<ConfigMap> {

  public static final String INDEX_DELIMITER = "-";

  @Override
  public Map<String, ConfigMap> getSecondaryResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return context
        .getSecondaryResourcesAsStream(ConfigMap.class)
        .filter(cm -> getName(cm).startsWith(primary.getMetadata().getName()))
        .collect(
            Collectors.toMap(
                cm -> getName(cm).substring(getName(cm).lastIndexOf(INDEX_DELIMITER) + 1),
                Function.identity()));
  }

  private static String getName(ConfigMap cm) {
    return cm.getMetadata().getName();
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigMap resource) {
    return Mappers.fromOwnerReferences(BulkDependentTestCustomResource.class, false)
        .toPrimaryResourceIDs(resource);
  }
}
