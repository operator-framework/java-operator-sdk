package io.javaoperatorsdk.operator.sample.informerconnectivity;

import io.javaoperatorsdk.operator.api.reconciler.*;

/**
 * Copies the config map value from spec into status. The main purpose is to test and demonstrate
 * sample usage of InformerEventSource
 */
@ControllerConfiguration
public class SimpleConnectivityTestCustomReconciler
    implements Reconciler<InformerConnectivityTestCustomResource> {

  @Override
  public UpdateControl<InformerConnectivityTestCustomResource> reconcile(
      InformerConnectivityTestCustomResource resource,
      Context<InformerConnectivityTestCustomResource> context) {


    return UpdateControl.noUpdate();
  }

}
