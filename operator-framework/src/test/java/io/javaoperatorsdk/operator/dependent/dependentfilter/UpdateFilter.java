package io.javaoperatorsdk.operator.dependent.dependentfilter;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.dependent.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;
import static io.javaoperatorsdk.operator.dependent.dependentfilter.DependentFilterTestReconciler.CONFIG_MAP_FILTER_VALUE;

public class UpdateFilter implements OnUpdateFilter<ConfigMap> {
  @Override
  public boolean accept(ConfigMap resource, ConfigMap oldResource) {
    return !resource.getData().get(CM_VALUE_KEY).equals(CONFIG_MAP_FILTER_VALUE);
  }
}
