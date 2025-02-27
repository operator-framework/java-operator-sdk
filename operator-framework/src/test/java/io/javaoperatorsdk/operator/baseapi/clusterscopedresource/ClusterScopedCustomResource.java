package io.javaoperatorsdk.operator.baseapi.clusterscopedresource;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("csc")
public class ClusterScopedCustomResource
    extends CustomResource<ClusterScopedCustomResourceSpec, ClusterScopedCustomResourceStatus> {}
