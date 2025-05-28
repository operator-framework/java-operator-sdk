package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class FirstService extends BaseService {
  public static final String DISCRIMINATOR_PREFIX = "first";

  public FirstService() {
    super(DISCRIMINATOR_PREFIX);
  }
}
