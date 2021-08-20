package io.javaoperatorsdk.operator.sample.simple;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk.io")
@Version("v1")
public class TestCustomResource
    extends CustomResource<TestCustomResourceSpec, TestCustomResourceStatus> {
}
