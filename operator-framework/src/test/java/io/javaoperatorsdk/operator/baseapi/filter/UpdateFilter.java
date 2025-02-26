package io.javaoperatorsdk.operator.baseapi.filter;

import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.baseapi.filter.FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE;

public class UpdateFilter implements OnUpdateFilter<FilterTestCustomResource> {
  @Override
  public boolean accept(FilterTestCustomResource resource, FilterTestCustomResource oldResource) {
    return !resource.getSpec().getValue().equals(CUSTOM_RESOURCE_FILTER_VALUE);
  }
}
