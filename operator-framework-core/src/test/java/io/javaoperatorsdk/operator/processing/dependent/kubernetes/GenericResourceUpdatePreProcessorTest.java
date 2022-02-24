package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ResourceUpdatePreProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericResourceUpdatePreProcessorTest {

  private static final Context context = mock(Context.class);

  static {
    final var configurationService = mock(ConfigurationService.class);
    when(configurationService.getResourceCloner()).thenReturn(ConfigurationService.DEFAULT_CLONER);
    when(context.getConfigurationService()).thenReturn(configurationService);
  }

  ResourceUpdatePreProcessor<Deployment> processor =
      GenericResourceUpdatePreProcessor.processorFor(Deployment.class);


  @Test
  void preservesValues() {
    var desired = createDeployment();
    var actual = createDeployment();
    actual.getMetadata().setLabels(new HashMap<>());
    actual.getMetadata().getLabels().put("additionalActualKey", "value");
    actual.getMetadata().setResourceVersion("1234");
    actual.getSpec().setRevisionHistoryLimit(5);

    var result = processor.replaceSpecOnActual(actual, desired, context);

    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec().getRevisionHistoryLimit()).isEqualTo(10);
  }

  Deployment createDeployment() {
    return ReconcilerUtils.loadYaml(
        Deployment.class, GenericResourceUpdatePreProcessorTest.class, "nginx-deployment.yaml");
  }

}
