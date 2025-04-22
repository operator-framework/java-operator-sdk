package io.javaoperatorsdk.operator.baseapi.statuscache.internal;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("spcl")
public class StatusPatchCacheCustomResource
    extends CustomResource<StatusPatchCacheSpec, StatusPatchCacheStatus> implements Namespaced {}
