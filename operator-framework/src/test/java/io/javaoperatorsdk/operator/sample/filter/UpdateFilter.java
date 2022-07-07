package io.javaoperatorsdk.operator.sample.filter;

import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;

import static io.javaoperatorsdk.operator.sample.filter.FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE;

public class UpdateFilter implements EventFilter<FilterTestCustomResource> {

  @Override
  public boolean acceptsUpdating(FilterTestCustomResource from, FilterTestCustomResource to) {
    return !to.getSpec().getValue().equals(CUSTOM_RESOURCE_FILTER_VALUE);
  }
}
