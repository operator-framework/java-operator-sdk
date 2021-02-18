package io.javaoperatorsdk.quarkus.it;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

public interface RegistrableController<R extends CustomResource> extends ResourceController<R> {
  public boolean isInitialised();
}
