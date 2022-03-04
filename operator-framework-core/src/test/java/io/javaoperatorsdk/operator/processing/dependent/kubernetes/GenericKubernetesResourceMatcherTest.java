package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericKubernetesResourceMatcherTest {

  private static final Context context = mock(Context.class);
  static {
    final var configurationService = mock(ConfigurationService.class);
    when(configurationService.getObjectMapper()).thenReturn(new ObjectMapper());
    when(context.getConfigurationService()).thenReturn(configurationService);
  }

  @Test
  void checksIfDesiredValuesAreTheSame() {
    var actual = createDeployment();
    final var desired = createDeployment();
    final var matcher = GenericKubernetesResourceMatcher.matcherFor(Deployment.class,
        new KubernetesDependentResource<>() {
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
        });
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
}
