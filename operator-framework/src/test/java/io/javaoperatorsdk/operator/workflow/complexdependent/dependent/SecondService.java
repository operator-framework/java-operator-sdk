package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent()
public class SecondService extends BaseService {

  public static final String DISCRIMINATOR_PREFIX = "second";

  public SecondService() {
    super(DISCRIMINATOR_PREFIX);
  }
}
