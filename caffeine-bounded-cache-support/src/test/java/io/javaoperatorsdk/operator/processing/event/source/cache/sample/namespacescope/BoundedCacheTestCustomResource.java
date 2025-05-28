package io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("bct")
public class BoundedCacheTestCustomResource
    extends CustomResource<BoundedCacheTestSpec, BoundedCacheTestStatus> implements Namespaced {}
