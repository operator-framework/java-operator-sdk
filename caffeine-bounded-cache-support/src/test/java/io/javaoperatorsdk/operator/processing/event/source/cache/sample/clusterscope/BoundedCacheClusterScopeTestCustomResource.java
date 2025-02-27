package io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestStatus;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("bccs")
public class BoundedCacheClusterScopeTestCustomResource
    extends CustomResource<BoundedCacheTestSpec, BoundedCacheTestStatus> {}
