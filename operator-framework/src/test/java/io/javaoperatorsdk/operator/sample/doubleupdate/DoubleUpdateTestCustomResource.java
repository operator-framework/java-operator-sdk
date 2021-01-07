package io.javaoperatorsdk.operator.sample.doubleupdate;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class DoubleUpdateTestCustomResource
    extends CustomResource<
        DoubleUpdateTestCustomResourceSpec, DoubleUpdateTestCustomResourceStatus> {}
