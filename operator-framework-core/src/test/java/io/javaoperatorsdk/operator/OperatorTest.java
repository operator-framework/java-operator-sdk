package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SuppressWarnings("rawtypes")
class OperatorTest {

  private final KubernetesClient kubernetesClient = MockKubernetesClient.client(ConfigMap.class);
  private final Operator operator = new Operator(kubernetesClient);
  private final FooReconciler fooReconciler = new FooReconciler();

  @BeforeAll
  @AfterAll
  static void setUpConfigurationServiceProvider() {
    ConfigurationServiceProvider.reset();
  }

  @Test
  @DisplayName("should register `Reconciler` to Controller")
  public void shouldRegisterReconcilerToController() {
    // given
    final var configuration = MockControllerConfiguration.forResource(ConfigMap.class);

    // when
    operator.register(fooReconciler, configuration);

    // then
    assertThat(operator.getControllers().size()).isEqualTo(1);
    assertThat(operator.getControllers().get(0).getReconciler()).isEqualTo(fooReconciler);
  }

  @Test
  @DisplayName("should throw `OperationException` when Configuration is null")
  public void shouldThrowOperatorExceptionWhenConfigurationIsNull() {
    // use a ConfigurationService that doesn't automatically create configurations
    ConfigurationServiceProvider.reset();
    ConfigurationServiceProvider.set(new AbstractConfigurationService(null));

    Assertions.assertThrows(OperatorException.class, () -> operator.register(fooReconciler));
  }

  private static class FooReconciler implements Reconciler<ConfigMap> {

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context context) {
      return UpdateControl.noUpdate();
    }
  }

}
