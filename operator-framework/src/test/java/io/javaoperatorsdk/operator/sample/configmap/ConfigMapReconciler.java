package io.javaoperatorsdk.operator.sample.configmap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.NO_FINALIZER;

@ControllerConfiguration(finalizerName = NO_FINALIZER)
public class ConfigMapReconciler
    implements Reconciler<ConfigMap>, TestExecutionInfoProvider {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapReconciler.class);
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ConfigMap> reconcile(
          ConfigMap resource, Context context) {

    log.info("Reconcile config map: {}",resource.getMetadata().getName());
    numberOfExecutions.incrementAndGet();
    return UpdateControl.noUpdate();
  }


  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
