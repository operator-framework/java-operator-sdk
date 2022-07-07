package io.javaoperatorsdk.operator.sample.dependentfilter;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;

import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CM_VALUE_KEY;
import static io.javaoperatorsdk.operator.sample.dependentfilter.DependentFilterTestReconciler.CONFIG_MAP_FILTER_VALUE;

public class UpdateFilter implements EventFilter<ConfigMap> {

  @Override
  public boolean acceptsUpdating(ConfigMap from, ConfigMap to) {
    return !to.getData().get(CM_VALUE_KEY).equals(CONFIG_MAP_FILTER_VALUE);
  }
}
