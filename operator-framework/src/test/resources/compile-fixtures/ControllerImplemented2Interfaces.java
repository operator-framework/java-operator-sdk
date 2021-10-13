package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.io.Serializable;

@Controller
public class ControllerImplemented2Interfaces implements Serializable, ResourceController<ControllerImplemented2Interfaces.MyCustomResource> {

    public static class MyCustomResource extends CustomResource {
    }

    @Override
    public UpdateControl<MyCustomResource> createOrUpdateResource(MyCustomResource customResource, Context<MyCustomResource> context) {
        return UpdateControl.updateCustomResource(null);
    }

    @Override
    public DeleteControl deleteResource(MyCustomResource customResource, Context<MyCustomResource> context) {
        return DeleteControl.defaultDelete();
    }
}
