package io.javaoperatorsdk.operator.sample.filter;

import java.util.function.BiPredicate;

import static io.javaoperatorsdk.operator.sample.filter.FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE;

public class UpdateFilter
    implements BiPredicate<FilterTestCustomResource, FilterTestCustomResource> {
  @Override
  public boolean test(FilterTestCustomResource resource, FilterTestCustomResource oldResource) {
    return !resource.getSpec().getValue().equals(CUSTOM_RESOURCE_FILTER_VALUE);
  }
}
