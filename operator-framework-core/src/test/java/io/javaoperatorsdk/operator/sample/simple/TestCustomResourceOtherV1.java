package io.javaoperatorsdk.operator.sample.simple;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk.io")
@Version("v1")
@Kind("TestCustomResourceOtherV1") // this is needed to override the automatically generated kind
public class TestCustomResourceOtherV1
    extends CustomResource<TestCustomResourceSpec, TestCustomResourceStatus> {}
