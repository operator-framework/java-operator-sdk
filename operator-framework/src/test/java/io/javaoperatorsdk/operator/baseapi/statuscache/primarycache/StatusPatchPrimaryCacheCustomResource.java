package io.javaoperatorsdk.operator.baseapi.statuscache.primarycache;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("spc")
public class StatusPatchPrimaryCacheCustomResource
    extends CustomResource<StatusPatchPrimaryCacheSpec, StatusPatchPrimaryCacheStatus>
    implements Namespaced {}
