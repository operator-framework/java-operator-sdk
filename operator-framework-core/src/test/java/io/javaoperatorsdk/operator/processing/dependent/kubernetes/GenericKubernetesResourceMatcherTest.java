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
import io.javaoperatorsdk.operator.processing.dependent.Matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class GenericKubernetesResourceMatcherTest {

  private static final Context context = mock(Context.class);

  Deployment actual = createDeployment();
  Deployment desired = createDeployment();
  TestDependentResource dependentResource = new TestDependentResource(desired);
  Matcher matcher =
      GenericKubernetesResourceMatcher.matcherFor(Deployment.class, dependentResource);

  @BeforeAll
  static void setUp() {
    final var controllerConfiguration = mock(ControllerConfiguration.class);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
  }

  @Test
  void matchesTrivialCases() {
    assertThat(matcher.match(actual, null, context).matched()).isTrue();
    assertThat(matcher.match(actual, null, context).computedDesired()).isPresent();
    assertThat(matcher.match(actual, null, context).computedDesired()).contains(desired);
  }

  @Test
  void matchesAdditiveOnlyChanges() {
    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(matcher.match(actual, null, context).matched())
        .withFailMessage("Additive changes should be ok")
        .isTrue();
  }

  @Test
  void matchesWithStrongSpecEquality() {
    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, true, true).matched())
        .withFailMessage("Strong equality does not ignore additive changes on spec")
        .isFalse();
  }

  @Test
  void notMatchesRemovedValues() {
    actual = createDeployment();
    assertThat(matcher.match(actual, createPrimary("removed"), context).matched())
        .withFailMessage("Removed value should not be ok")
        .isFalse();
  }

  @Test
  void notMatchesChangedValues() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(matcher.match(actual, null, context).matched())
        .withFailMessage("Changed values are not ok")
        .isFalse();
  }

  @Test
  void notMatchesIgnoredPaths() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, false, "/spec/replicas").matched())
        .withFailMessage("Ignored paths are not matched")
        .isTrue();
  }

  @Test
  void ignoresWholeSubPath() {
    actual = createDeployment();
    actual.getSpec().getTemplate().getMetadata().getLabels().put("additionak-key", "val");
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, false, "/spec/template").matched())
        .withFailMessage("Ignored sub-paths are not matched")
        .isTrue();
  }

  @Test
  void matchesMetadata() {
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
        .match(dependentResource, actual, null, context, true, true, true).matched())
        .withFailMessage("Annotations should matter when metadata is considered")
        .isFalse();

    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, true, false).matched())
        .withFailMessage("Non strong equality on labels and annotations")
        .isTrue();

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
