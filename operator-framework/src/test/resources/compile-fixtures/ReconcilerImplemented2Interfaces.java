package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.io.Serializable;

@Controller
public class ReconcilerImplemented2Interfaces implements Serializable, Reconciler<ReconcilerImplemented2Interfaces.MyCustomResource> {

    public static class MyCustomResource extends CustomResource<Void,Void> {
    }

    @Override
    public UpdateControl<MyCustomResource> createOrUpdateResources(MyCustomResource customResource, Context context) {
        return UpdateControl.updateCustomResource(null);
    }

    @Override
    public DeleteControl deleteResources(MyCustomResource customResource, Context context) {
        return DeleteControl.defaultDelete();
    }
}
