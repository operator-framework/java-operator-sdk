package io.javaoperatorsdk.operator.sample.subresource;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class SubResourceTestCustomResource
    extends CustomResource<
        SubResourceTestCustomResourceSpec, SubResourceTestCustomResourceStatus> {}
