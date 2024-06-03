package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.client.KubernetesClient;


// based on
// https://github.com/kubernetes/kubernetes/blob/master/staging/src/k8s.io/client-go/util/csaupgrade/upgrade.go
public class SSAUpgradeUtils {

  public static <R> R upgradeManagedFields(R resource, KubernetesClient client) {

    return resource;
  }

}
