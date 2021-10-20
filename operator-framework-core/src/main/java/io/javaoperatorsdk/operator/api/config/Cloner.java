package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;

public interface Cloner {

  CustomResource<?, ?> clone(CustomResource<?, ?> object);

}
