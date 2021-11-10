package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.io.Serializable;

@ControllerConfiguration
public class ReconcilerImplemented2Interfaces implements Serializable, Reconciler<ReconcilerImplemented2Interfaces.MyCustomResource> {

    public static class MyCustomResource extends CustomResource<Void,Void> {
    }

    @Override
    public UpdateControl<MyCustomResource> reconcile(MyCustomResource customResource, Context context) {
        return UpdateControl.updateCustomResource(null);
    }

    @Override
    public DeleteControl cleanup(MyCustomResource customResource, Context context) {
        return DeleteControl.defaultDelete();
    }
}
