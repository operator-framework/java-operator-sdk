package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class GenericResourceUpdater {

  private static final String METADATA = "metadata";

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> R updateResource(R actual, R desired, Context<?> context) {
    KubernetesSerialization kubernetesSerialization =
        context.getClient().getKubernetesSerialization();
    Map<String, Object> actualMap = kubernetesSerialization.convertValue(actual, Map.class);
    Map<String, Object> desiredMap = kubernetesSerialization.convertValue(desired, Map.class);
    // replace all top level fields from actual with desired, but merge metadata separately
    // note that this ensures that `resourceVersion` is present, therefore optimistic
    // locking will happen on server side
    var metadata = actualMap.remove(METADATA);
    actualMap.replaceAll((k, v) -> desiredMap.get(k));
    actualMap.putAll(desiredMap);
    actualMap.put(METADATA, metadata);
    var clonedActual = (R) kubernetesSerialization.convertValue(actualMap, desired.getClass());
    updateLabelsAndAnnotation(clonedActual, desired);
    return clonedActual;
  }

  public static <K extends HasMetadata> void updateLabelsAndAnnotation(K actual, K desired) {
    actual.getMetadata().getLabels().putAll(desired.getMetadata().getLabels());
    actual.getMetadata().getAnnotations().putAll(desired.getMetadata().getAnnotations());
  }
}
