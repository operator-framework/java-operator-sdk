package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
class GenericResourceUpdatePreProcessorTest {

  private static final Context context = mock(Context.class);

  @BeforeAll
  static void setUp() {
    final var controllerConfiguration = mock(ControllerConfiguration.class);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
  }

  @Test
  void preservesValues() {
    var processor = GenericResourceUpdatePreProcessor.processorFor(Deployment.class);
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

  @Test
  void checkNamespaces() {
    var processor = GenericResourceUpdatePreProcessor.processorFor(Namespace.class);
    var desired = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();
    var actual = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();
    actual.getMetadata().setLabels(new HashMap<>());
    actual.getMetadata().getLabels().put("additionalActualKey", "value");
    actual.getMetadata().setResourceVersion("1234");

    var result = processor.replaceSpecOnActual(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");

    desired.setSpec(new NamespaceSpec(List.of("halkyon.io/finalizer")));

    result = processor.replaceSpecOnActual(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec().getFinalizers()).containsExactly("halkyon.io/finalizer");

    desired = new NamespaceBuilder().withNewMetadata().withName("foo").endMetadata().build();

    result = processor.replaceSpecOnActual(actual, desired, context);
    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec()).isNull();
  }

  Deployment createDeployment() {
    return ReconcilerUtils.loadYaml(
        Deployment.class, GenericResourceUpdatePreProcessorTest.class, "nginx-deployment.yaml");
  }

}