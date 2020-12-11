package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.io.Serializable;


public abstract class AbstractController implements Serializable, ResourceController<AbstractController.MyCustomResource> {
    public static class MyCustomResource extends CustomResource {

    }
}
