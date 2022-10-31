package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = FirstService.Discriminator.class)
public class FirstService extends BaseService {
  public static final String DISCRIMINATOR_PREFIX = "first";

  public FirstService() {
    super(DISCRIMINATOR_PREFIX);
  }

  public static class Discriminator extends NamePrefixResourceDiscriminator<Service> {
    protected Discriminator() {
      super(DISCRIMINATOR_PREFIX);
    }
  }

}
