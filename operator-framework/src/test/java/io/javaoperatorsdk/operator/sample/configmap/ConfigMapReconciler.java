package io.javaoperatorsdk.operator.sample.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class ConfigMapReconciler
    implements Reconciler<ConfigMap>, KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapReconciler.class);

  private KubernetesClient kubernetesClient;

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public UpdateControl<ConfigMap> reconcile(
          ConfigMap resource, Context context) {

    log.info("Reconcile config map: {}",resource);

    return UpdateControl.noUpdate();
  }


}
