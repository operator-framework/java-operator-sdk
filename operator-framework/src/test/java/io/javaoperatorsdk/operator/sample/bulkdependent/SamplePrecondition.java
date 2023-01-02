package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class SamplePrecondition
    implements Condition<Map<String, ConfigMap>, BulkDependentTestCustomResource> {

  public static final String SKIP_RESOURCE_DATA = "skipThis";

  @Override
  public boolean isMet(BulkDependentTestCustomResource primary, Map<String, ConfigMap> secondary,
      Context<BulkDependentTestCustomResource> context) {
    return !SKIP_RESOURCE_DATA.equals(primary.getSpec().getAdditionalData());
  }
}
