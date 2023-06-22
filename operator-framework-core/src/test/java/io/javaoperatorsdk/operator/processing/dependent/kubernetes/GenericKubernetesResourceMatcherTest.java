package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher.GenericResourceUpdaterMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class GenericKubernetesResourceMatcherTest {

  private static final Context context = mock(Context.class);

  Deployment actual = createDeployment();
  Deployment desired = createDeployment();
  TestDependentResource dependentResource = new TestDependentResource(desired);
  Matcher matcher = GenericKubernetesResourceMatcher.matcherFor(dependentResource);

  @BeforeAll
  static void setUp() {
    final var client = MockKubernetesClient.client(HasMetadata.class);
    when(context.getClient()).thenReturn(client);
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
        .withFailMessage("Additive changes should not cause a mismatch by default")
        .isTrue();
  }

  @Test
  void matchesWithStrongSpecEquality() {
    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, true, true,
            true)
        .matched())
        .withFailMessage("Adding values should fail matching when strong equality is required")
        .isFalse();
  }

  @Test
  void doesNotMatchRemovedValues() {
    actual = createDeployment();
    assertThat(matcher.match(actual, createPrimary("removed"), context).matched())
        .withFailMessage("Removing values in metadata should lead to a mismatch")
        .isFalse();
  }

  @Test
  void doesNotMatchChangedValues() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(matcher.match(actual, null, context).matched())
        .withFailMessage("Should not have matched because values have changed")
        .isFalse();
  }

  @Test
  void doesNotMatchChangedValuesWhenNoIgnoredPathsAreProvided() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, true).matched())
        .withFailMessage(
            "Should not have matched because values have changed and no ignored path is provided")
        .isFalse();
  }

  @Test
  void doesNotAttemptToMatchIgnoredPaths() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, false, "/spec/replicas").matched())
        .withFailMessage("Should not have compared ignored paths")
        .isTrue();
  }

  @Test
  void ignoresWholeSubPath() {
    actual = createDeployment();
    actual.getSpec().getTemplate().getMetadata().getLabels().put("additional-key", "val");
    assertThat(GenericKubernetesResourceMatcher
        .match(dependentResource, actual, null, context, false, "/spec/template").matched())
        .withFailMessage("Should match when only changes impact ignored sub-paths")
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
        .withFailMessage(
            "Should match when strong equality is not considered and only additive changes are made")
        .isTrue();

  }

  @Test
  void checkServiceAccount() {
    final var serviceAccountDR = new ServiceAccountDR();

    final var desired = serviceAccountDR.desired(null, context);
    var actual = new ServiceAccountBuilder(desired)
        .addNewImagePullSecret("imagePullSecret3")
        .build();

    final var matcher = GenericResourceUpdaterMatcher.updaterMatcherFor(ServiceAccount.class);
    assertThat(matcher.matches(actual, desired, context)).isFalse();
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

  private static class ServiceAccountDR
      extends KubernetesDependentResource<ServiceAccount, HasMetadata> {

    public ServiceAccountDR() {
      super(ServiceAccount.class);
    }

    @Override
    protected ServiceAccount desired(HasMetadata primary, Context<HasMetadata> context) {
      return new ServiceAccountBuilder()
          .withNewMetadata().withName("foo").endMetadata()
          .withAutomountServiceAccountToken()
          .addNewImagePullSecret("imagePullSecret1")
          .addNewImagePullSecret("imagePullSecret2")
          .build();
    }
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
