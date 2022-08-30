package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.sample.informerconnectivity.InformerConnectivityTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.informerconnectivity.SimpleConnectivityTestCustomReconciler;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class InformerConnectivityIT {

  public static final String NO_CR_ACCESS_USER = "noCRAccessUser";

  @Test
  void notStartsIfNoCustomResourceRBACAccess() {

    // todo apply cluster role, dedicated namespace
    // var role = ReconcilerUtils.loadYaml(ClusterRole.class,this.getClass(),"noaccessrole.yaml");

    KubernetesClient client = new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder()
            .withImpersonateUsername(NO_CR_ACCESS_USER)
            .build())
        .build();
    Operator o = new Operator(client);
    o.register(new SimpleConnectivityTestCustomReconciler());

    assertThrows(OperatorException.class, o::start);
  }

  @Test
  void notStartsWhenNoAccessToSecondaryInformersResource() {
    KubernetesClient client = new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder()
            .withImpersonateUsername(NO_CR_ACCESS_USER)
            .build())
        .build();
    Operator o = new Operator(client);
    o.register(new InformerConnectivityTestCustomReconciler());

    assertThrows(OperatorException.class, o::start);
  }

  // todo add permission in runtime
  @Test
  void startsIfReconnectModeConfiguredAndNoCRAccess() {

  }

  @Test
  void startsIfReconnectModeConfiguredAndNoInformerResourceAccess() {

  }



}
