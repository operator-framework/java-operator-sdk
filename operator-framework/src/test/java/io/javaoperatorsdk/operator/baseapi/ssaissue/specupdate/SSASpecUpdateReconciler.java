package io.javaoperatorsdk.operator.baseapi.ssaissue.specupdate;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class SSASpecUpdateReconciler
    implements Reconciler<SSASpecUpdateCustomResource>, Cleaner<SSASpecUpdateCustomResource> {

  @Override
  public UpdateControl<SSASpecUpdateCustomResource> reconcile(
      SSASpecUpdateCustomResource resource, Context<SSASpecUpdateCustomResource> context) {

    var copy = createFreshCopy(resource);
    copy.getSpec().setValue("value");
    context.getClient().resource(copy).fieldManager(context.getControllerConfiguration()
            .fieldManager())
            .serverSideApply();

    return UpdateControl.noUpdate();
  }

  SSASpecUpdateCustomResource createFreshCopy(SSASpecUpdateCustomResource resource) {
    var res = new SSASpecUpdateCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    res.setSpec(new SSASpecUpdateCustomResourceSpec());
    return res;
  }

  @Override
  public DeleteControl cleanup(
      SSASpecUpdateCustomResource resource, Context<SSASpecUpdateCustomResource> context) {

    return DeleteControl.defaultDelete();
  }
}
