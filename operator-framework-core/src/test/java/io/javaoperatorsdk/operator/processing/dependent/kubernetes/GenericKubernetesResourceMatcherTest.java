package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher.match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked"})
class GenericKubernetesResourceMatcherTest {

  private static final Context context = mock(Context.class);

  Deployment actual = createDeployment();
  Deployment desired = createDeployment();
  TestDependentResource dependentResource = new TestDependentResource(desired);

  @BeforeAll
  static void setUp() {
    final var client = MockKubernetesClient.client(HasMetadata.class);
    when(context.getClient()).thenReturn(client);
  }

  @Test
  void matchesTrivialCases() {
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).matched()).isTrue();
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).computedDesired())
        .isPresent();
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).computedDesired())
        .contains(desired);
  }

  @Test
  void matchesAdditiveOnlyChanges() {
    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).matched())
        .withFailMessage("Additive changes should not cause a mismatch by default")
        .isTrue();
  }

  @Test
  void matchesWithStrongSpecEquality() {
    actual.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(match(desired, actual, true, true, context).matched())
        .withFailMessage("Adding values should fail matching when strong equality is required")
        .isFalse();
  }

  @Test
  void doesNotMatchRemovedValues() {
    actual = createDeployment();
    assertThat(
            GenericKubernetesResourceMatcher.match(
                    dependentResource.desired(createPrimary("removed"), null), actual, context)
                .matched())
        .withFailMessage("Removing values in metadata should lead to a mismatch")
        .isFalse();
  }

  @Test
  void doesNotMatchChangedValues() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).matched())
        .withFailMessage("Should not have matched because values have changed")
        .isFalse();
  }

  @Test
  void ignoreStatus() {
    actual = createDeployment();
    actual.setStatus(new DeploymentStatusBuilder().withReadyReplicas(1).build());
    assertThat(GenericKubernetesResourceMatcher.match(desired, actual, context).matched())
        .withFailMessage("Should ignore status in actual")
        .isTrue();
  }

  @Test
  void doesNotMatchChangedValuesWhenNoIgnoredPathsAreProvided() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(match(dependentResource, actual, null, context, true).matched())
        .withFailMessage(
            "Should not have matched because values have changed and no ignored path is provided")
        .isFalse();
  }

  @Test
  void doesNotAttemptToMatchIgnoredPaths() {
    actual = createDeployment();
    actual.getSpec().setReplicas(2);
    assertThat(match(dependentResource, actual, null, context, false, "/spec/replicas").matched())
        .withFailMessage("Should not have compared ignored paths")
        .isTrue();
  }

  @Test
  void ignoresWholeSubPath() {
    actual = createDeployment();
    actual.getSpec().getTemplate().getMetadata().getLabels().put("additional-key", "val");
    assertThat(match(dependentResource, actual, null, context, false, "/spec/template").matched())
        .withFailMessage("Should match when only changes impact ignored sub-paths")
        .isTrue();
  }

  @Test
  void matchesMetadata() {
    actual =
        new DeploymentBuilder(createDeployment())
            .editOrNewMetadata()
            .addToAnnotations("test", "value")
            .endMetadata()
            .build();
    assertThat(match(dependentResource, actual, null, context, false).matched())
        .withFailMessage("Annotations shouldn't matter when metadata is not considered")
        .isTrue();

    assertThat(match(desired, actual, true, true, context).matched())
        .withFailMessage("Annotations should matter when metadata is considered")
        .isFalse();

    assertThat(match(desired, actual, false, false, context).matched())
        .withFailMessage(
            "Should match when strong equality is not considered and only additive changes are"
                + " made")
        .isTrue();
  }

  @Test
  void checkServiceAccount() {
    final var serviceAccountDR = new ServiceAccountDR();

    final var desired = serviceAccountDR.desired(null, context);
    var actual =
        new ServiceAccountBuilder(desired).addNewImagePullSecret("imagePullSecret3").build();

    assertThat(
            GenericKubernetesResourceMatcher.match(desired, actual, false, false, context)
                .matched())
        .isTrue();
  }

  @Test
  void matchConfigMap() {
    var desired = createConfigMap();
    var actual = createConfigMap();
    actual.getData().put("key2", "val2");

    var match = GenericKubernetesResourceMatcher.match(desired, actual, true, false, context);
    assertThat(match.matched()).isTrue();
  }

  ConfigMap createConfigMap() {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder().withName("tes1").withNamespace("default").build())
        .withData(Map.of("key1", "val1"))
        .build();
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

    @Override
    protected ServiceAccount desired(HasMetadata primary, Context<HasMetadata> context) {
      return new ServiceAccountBuilder()
          .withNewMetadata()
          .withName("foo")
          .endMetadata()
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
      final var currentCase =
          Optional.ofNullable(primary)
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
