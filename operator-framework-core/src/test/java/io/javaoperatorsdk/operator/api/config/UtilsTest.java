package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

  @Test
  void getsFirstTypeArgumentFromExtendedClass() {
    Class<?> res =
        Utils.getFirstTypeArgumentFromExtendedClass(TestKubernetesDependentResource.class);
    assertThat(res).isEqualTo(Deployment.class);
  }

  public static class TestKubernetesDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {

    @Override
    protected Optional<Deployment> desired(TestCustomResource primary, Context context) {
      return Optional.empty();
    }
  }
}
