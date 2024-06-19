package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdaterMatcher;

public class GenericResourceUpdaterMatcher<R extends HasMetadata> implements
    ResourceUpdaterMatcher<R> {

  private static final String METADATA = "metadata";
  private static final ResourceUpdaterMatcher<?> INSTANCE = new GenericResourceUpdaterMatcher<>();

  protected GenericResourceUpdaterMatcher() {}

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> ResourceUpdaterMatcher<R> updaterMatcherFor() {
    return (ResourceUpdaterMatcher<R>) INSTANCE;
  }

  @SuppressWarnings("unchecked")
  @Override
  public R updateResource(R actual, R desired, Context<?> context) {
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

  @Override
  public boolean matches(R actual, R desired, Context<?> context) {
    return GenericKubernetesResourceMatcher.match(desired, actual,
        false, false, context).matched();
  }

  public static <K extends HasMetadata> void updateLabelsAndAnnotation(K actual, K desired) {
    actual.getMetadata().getLabels().putAll(desired.getMetadata().getLabels());
    actual.getMetadata().getAnnotations().putAll(desired.getMetadata().getAnnotations());
  }

}
