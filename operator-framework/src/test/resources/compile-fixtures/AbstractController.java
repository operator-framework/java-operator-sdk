package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.io.Serializable;


public abstract class AbstractController<T extends CustomResource> implements Serializable,
    ResourceController<T> {

  public static class MyCustomResource extends CustomResource {

  }
}
