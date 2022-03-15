package io.javaoperatorsdk.operator.sample.customfilter;

import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class CustomFlagFilter2 implements ResourceEventFilter<CustomFilteringTestResource> {

  @Override
  public boolean acceptChange(Controller<CustomFilteringTestResource> configuration,
      CustomFilteringTestResource oldResource, CustomFilteringTestResource newResource) {
    return newResource.getSpec().isFilter2();
  }
}
