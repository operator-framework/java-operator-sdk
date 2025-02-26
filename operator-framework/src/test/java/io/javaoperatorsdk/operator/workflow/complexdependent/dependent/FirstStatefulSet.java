package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class FirstStatefulSet extends BaseStatefulSet {

  public static final String DISCRIMINATOR_PREFIX = "first";

  public FirstStatefulSet() {
    super(DISCRIMINATOR_PREFIX);
  }
}
