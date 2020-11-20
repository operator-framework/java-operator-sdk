package io.javaoperatorsdk.operator.springboot.starter.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class TestResource extends CustomResource implements Namespaced {

}
