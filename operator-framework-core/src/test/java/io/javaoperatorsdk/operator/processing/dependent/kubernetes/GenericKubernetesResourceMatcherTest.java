package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class GenericKubernetesResourceMatcherTest {

  private static final Context context = mock(Context.class);

  @BeforeAll
  static void setUp() {
    final var controllerConfiguration = mock(ControllerConfiguration.class);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
  }

  @Test
  void checksIfDesiredValuesAreTheSame() {
    var actual = createDeployment();
    final var desired = createDeployment();
    final var dependentResource = new TestDependentResource(desired);
    final var matcher =
        GenericKubernetesResourceMatcher.matcherFor(Deployment.class, dependentResource);
    assertThat(matcher.match(actual, null, context).matched()).isTrue();
    assertThat(matcher.match(actual, null, context).computedDesired().isPresent()).isTrue();
    assertThat(matcher.match(actual, null, context).computedDesired().get()).isEqualTo(desired);

    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(matcher.match(actual, null, context).matched())
        .withFailMessage("Additive changes should be ok")
        .isTrue();

    actual = createDeployment();
    assertThat(matcher.match(actual, createPrimary("removed"), context).matched())
        .withFailMessage("Removed value should not be ok")
        .isFalse();

    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(matcher.match(actual, null, context).matched())
        .withFailMessage("Changed values are not ok")
        .isFalse();

    actual = new DeploymentBuilder(createDeployment())
        .editOrNewMetadata()
        .addToAnnotations("test", "value")
        .endMetadata()
        .build();
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, false).matched())
        .withFailMessage("Annotations shouldn't matter when metadata is not considered")
        .isTrue();

    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, true).matched())
        .withFailMessage("Annotations should matter when metadata is considered")
        .isFalse();
  }

  Deployment createDeployment() {
    return ReconcilerUtils.loadYaml(
        Deployment.class, GenericKubernetesResourceMatcherTest.class, "nginx-deployment.yaml");
  }

  HasMetadata createPrimary(String caseName) {
    return new DeploymentBuilder()
        .editOrNewMetadata()
        .addToLabels("case", caseName)
        .endMetadata()
        .build();
  }

  private class TestDependentResource extends KubernetesDependentResource<Deployment, HasMetadata> {

    private final Deployment desired;

    public TestDependentResource(Deployment desired) {
      super(Deployment.class);
      this.desired = desired;
    }

    @Override
    protected Deployment desired(HasMetadata primary, Context context) {
      final var currentCase = Optional.ofNullable(primary)
          .map(p -> p.getMetadata().getLabels().get("case"))
          .orElse(null);
      var d = desired;
      if ("removed".equals(currentCase)) {
        d = createDeployment();
        d.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
      }
      return d;
    }
  }
}
