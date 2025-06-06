package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.config.Utils.GENERIC_PARAMETER_TYPE_ERROR_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KubernetesDependentResourceTest {

  @ParameterizedTest
  @ValueSource(
      classes = {
        TestDeploymentDependentResource.class,
        ChildTestDeploymentDependentResource.class,
        GrandChildTestDeploymentDependentResource.class,
        ChildTypeWithValidKubernetesDependentResource.class,
        ConstructorOverridedCorrectDeployementDependentResource.class
      })
  void checkResourceTypeDerivationWithInheritance(Class<?> clazz) throws Exception {
    KubernetesDependentResource<?, ?> dependentResource =
        (KubernetesDependentResource<?, ?>) clazz.getDeclaredConstructor().newInstance();
    assertThat(dependentResource).isInstanceOf(KubernetesDependentResource.class);
    assertThat(dependentResource.resourceType()).isEqualTo(Deployment.class);
  }

  private static class TestDeploymentDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {}

  private static class ChildTestDeploymentDependentResource
      extends TestDeploymentDependentResource {}

  private static class GrandChildTestDeploymentDependentResource
      extends ChildTestDeploymentDependentResource {}

  private static class ChildTypeWithValidKubernetesDependentResource<P, T>
      extends KubernetesDependentResource<Deployment, TestCustomResource> {}

  private static class ConstructorOverridedCorrectDeployementDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {
    public ConstructorOverridedCorrectDeployementDependentResource() {
      super(Deployment.class);
    }
  }

  @Test
  void validateInvalidTypeDerivationTypesThrowException() {
    assertThatThrownBy(() -> new InvalidChildTestDeploymentDependentResource())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            GENERIC_PARAMETER_TYPE_ERROR_PREFIX
                + InvalidChildTestDeploymentDependentResource.class.getName()
                + " because it doesn't extend a class that is parametrized with the type we want to"
                + " retrieve or because it's Object.class. Please provide the resource type in the "
                + "constructor (e.g., super(Deployment.class).");
    assertThatThrownBy(() -> new InvalidGrandChildTestDeploymentDependentResource())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            GENERIC_PARAMETER_TYPE_ERROR_PREFIX
                + InvalidGrandChildTestDeploymentDependentResource.class.getName()
                + " because it doesn't extend a class that is parametrized with the type we want to"
                + " retrieve or because it's Object.class. Please provide the resource type in the "
                + "constructor (e.g., super(Deployment.class).");
  }

  private static class InvalidChildTestDeploymentDependentResource
      extends ChildTypeWithValidKubernetesDependentResource<Object, Object> {}

  private static class InvalidGrandChildTestDeploymentDependentResource
      extends InvalidChildTestDeploymentDependentResource {}
}
