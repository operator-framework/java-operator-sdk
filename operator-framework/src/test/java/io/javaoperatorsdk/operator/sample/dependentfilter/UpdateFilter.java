package io.javaoperatorsdk.operator.sample.dependentfilter;

import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.ConfigMap;

import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;
import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CONFIG_MAP_FILTER_VALUE;

public class UpdateFilter
    implements BiPredicate<ConfigMap, ConfigMap> {
  @Override
  public boolean test(ConfigMap resource, ConfigMap oldResource) {
    return !resource.getData().get(CM_VALUE_KEY).equals(CONFIG_MAP_FILTER_VALUE);
  }
}
