package io.javaoperatorsdk.operator.baseapi;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Sample(
    tldr = "Operator Startup with Informer Errors",
    description =
        """
        Demonstrates that the operator can start successfully even when informers encounter \
        errors during startup, such as insufficient access rights. By setting \
        stopOnInformerErrorDuringStartup to false, the operator gracefully handles permission \
        errors and continues initialization, allowing it to operate with partial access.
        """)
class InformerErrorHandlerStartIT {
  /** Test showcases that the operator starts even if there is no access right for some resource. */
  @Test
  @Timeout(5)
  void operatorStart() {
    KubernetesClient client =
        new KubernetesClientBuilder()
            .withConfig(new ConfigBuilder().withImpersonateUsername("user-with-no-rights").build())
            .build();

    Operator operator =
        new Operator(
            o ->
                o.withKubernetesClient(client)
                    .withStopOnInformerErrorDuringStartup(false)
                    .withCacheSyncTimeout(Duration.ofSeconds(2)));
    operator.register(new ConfigMapReconciler());
    operator.start();
  }

  @ControllerConfiguration
  public static class ConfigMapReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return UpdateControl.noUpdate();
    }
  }
}
