package io.javaoperatorsdk.operator.sample.filter;

import static io.javaoperatorsdk.operator.sample.filter.FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE;

import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public class UpdateFilter
    implements OnUpdateFilter<FilterTestCustomResource> {
  @Override
  public boolean accept(FilterTestCustomResource resource, FilterTestCustomResource oldResource) {
    return !resource.getSpec().getValue().equals(CUSTOM_RESOURCE_FILTER_VALUE);
  }
}
