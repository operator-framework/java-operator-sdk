package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(resourceDiscriminator = FirstStatefulSet.Discriminator.class)
public class FirstStatefulSet extends BaseStatefulSet {

  public static final String DISCRIMINATOR_PREFIX = "first";

  public FirstStatefulSet() {
    super(DISCRIMINATOR_PREFIX);
  }


  public static class Discriminator extends NamePrefixResourceDiscriminator<StatefulSet> {
    protected Discriminator() {
      super(DISCRIMINATOR_PREFIX);
    }
  }

}
