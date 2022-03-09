package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
class OperatorTest {

  private final KubernetesClient kubernetesClient = MockKubernetesClient.client(ConfigMap.class);
  private final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
  private final Operator operator = new Operator(kubernetesClient);
  private final FooReconciler fooReconciler = new FooReconciler();

  @Test
  @DisplayName("should register `Reconciler` to Controller")
  @SuppressWarnings("unchecked")
  public void shouldRegisterReconcilerToController() {
    // given
    when(configuration.getResourceClass()).thenReturn(ConfigMap.class);

    // when
    operator.register(fooReconciler, configuration);

    // then
    assertThat(operator.getControllers().size()).isEqualTo(1);
    assertThat(operator.getControllers().get(0).getReconciler()).isEqualTo(fooReconciler);
  }

  @Test
  @DisplayName("should throw `OperationException` when Configuration is null")
  public void shouldThrowOperatorExceptionWhenConfigurationIsNull() {
    Assertions.assertThrows(OperatorException.class, () -> operator.register(fooReconciler));
  }

  private static class FooReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }

}
