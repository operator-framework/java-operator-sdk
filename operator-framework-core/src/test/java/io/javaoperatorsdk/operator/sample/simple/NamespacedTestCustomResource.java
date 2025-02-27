package io.javaoperatorsdk.operator.sample.simple;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("namespaced-sample.javaoperatorsdk.io")
@Version("v1")
public class NamespacedTestCustomResource
    extends CustomResource<TestCustomResourceSpec, TestCustomResourceStatus>
    implements Namespaced {}
