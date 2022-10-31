package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = SecondService.Discriminator.class)
public class SecondService extends BaseService {

  public static final String DISCRIMINATOR_PREFIX = "second";

  public SecondService() {
    super(DISCRIMINATOR_PREFIX);
  }

  public static class Discriminator extends NamePrefixResourceDiscriminator<Service> {
    protected Discriminator() {
      super(DISCRIMINATOR_PREFIX);
    }
  }
}
