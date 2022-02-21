package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
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
    var target1 = createDeployment();
    var desired1 = createDeployment();
    final var matcher = GenericKubernetesResourceMatcher.matcherFor(Deployment.class);
    assertThat(matcher.match(target1, desired1, context)).isTrue();

    var target2 = createDeployment();
    var desired2 = createDeployment();
    target2.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(matcher.match(target2, desired2, context))
        .withFailMessage("Additive changes should be ok")
        .isTrue();

    var target3 = createDeployment();
    var desired3 = createDeployment();
    desired3.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(matcher.match(target3, desired3, context))
        .withFailMessage("Removed value should not be ok")
        .isFalse();

    var target4 = createDeployment();
    var desired4 = createDeployment();
    target4.getSpec().setReplicas(2);
    assertThat(matcher.match(target4, desired4, context))
        .withFailMessage("Changed values are not ok")
        .isFalse();
  }

  Deployment createDeployment() {
    return ReconcilerUtils.loadYaml(
        Deployment.class, GenericKubernetesResourceMatcherTest.class, "nginx-deployment.yaml");
  }
}
