package io.javaoperatorsdk.operator.sample.dependentfilter;

import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;
import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CONFIG_MAP_FILTER_VALUE;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class UpdateFilter
    implements OnUpdateFilter<ConfigMap> {
  @Override
  public boolean accept(ConfigMap resource, ConfigMap oldResource) {
    return !resource.getData().get(CM_VALUE_KEY).equals(CONFIG_MAP_FILTER_VALUE);
  }
}
