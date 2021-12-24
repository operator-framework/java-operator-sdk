package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperatorTest {

  private final KubernetesClient kubernetesClient =
      MockKubernetesClient.client(FooCustomResource.class);
  private final ConfigurationService configurationService = mock(ConfigurationService.class);
  private final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
  private final Operator operator = new Operator(kubernetesClient, configurationService);
  private final FooReconciler fooReconciler = FooReconciler.create();

  @Test
  @DisplayName("should register `Reconciler` to Controller")
  public void shouldRegisterReconcilerToController() {
    // given
    when(configurationService.getConfigurationFor(fooReconciler)).thenReturn(configuration);
    when(configuration.watchAllNamespaces()).thenReturn(true);
    when(configuration.getName()).thenReturn("FOO");
    when(configuration.getResourceClass()).thenReturn(FooCustomResource.class);
    when(configuration.getRetryConfiguration()).thenReturn(RetryConfiguration.DEFAULT);

    // when
    operator.register(fooReconciler);

    // then
    assertThat(operator.getControllers().size()).isEqualTo(1);
    assertThat(operator.getControllers().get(0).getReconciler()).isEqualTo(fooReconciler);
  }

  @Test
  @DisplayName("should throw `OperationException` when Configuration is null")
  public void shouldThrowOperatorExceptionWhenConfigurationIsNull() {
    Assertions.assertThrows(OperatorException.class, () -> operator.register(fooReconciler, null));
  }

  private static class FooCustomResource extends CustomResource<FooSpec, FooStatus> {
  }

  private static class FooSpec {
  }

  private static class FooStatus {
  }

  private static class FooReconciler implements Reconciler<FooCustomResource> {

    private FooReconciler() {}

    public static FooReconciler create() {
      return new FooReconciler();
    }

    @Override
    public UpdateControl<FooCustomResource> reconcile(FooCustomResource resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }

}
