package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class SecondStatefulSet extends BaseStatefulSet {

  public static final String DISCRIMINATOR_PREFIX = "second";

  public SecondStatefulSet() {
    super(DISCRIMINATOR_PREFIX);
  }
}
