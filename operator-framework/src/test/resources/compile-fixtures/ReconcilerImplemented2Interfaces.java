package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.*;

import java.io.Serializable;

@ControllerConfiguration
public class ReconcilerImplemented2Interfaces implements Serializable,
        Reconciler<ReconcilerImplemented2Interfaces.MyCustomResource>, Cleaner<ReconcilerImplemented2Interfaces.MyCustomResource> {

    public static class MyCustomResource extends CustomResource<Void,Void> {
    }

    @Override
    public UpdateControl<MyCustomResource> reconcile(MyCustomResource customResource, Context context) {
        return UpdateControl.updateResource(null);
    }

    @Override
    public DeleteControl cleanup(MyCustomResource customResource, Context context) {
        return DeleteControl.defaultDelete();
    }
}
