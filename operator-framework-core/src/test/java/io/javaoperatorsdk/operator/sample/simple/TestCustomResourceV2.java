package io.javaoperatorsdk.operator.sample.simple;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk.io")
@Version("v2")
@Kind("TestCustomResource")
public class TestCustomResourceV2
    extends CustomResource<TestCustomResourceSpec, TestCustomResourceStatus> {

}
