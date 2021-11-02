package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.Reconciler;
import io.javaoperatorsdk.operator.api.UpdateControl;
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
