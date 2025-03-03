package io.javaoperatorsdk.operator.baseapi.statuspatchnonlocking;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("spl")
public class StatusPatchLockingCustomResource
    extends CustomResource<
        StatusPatchLockingCustomResourceSpec, StatusPatchLockingCustomResourceStatus>
    implements Namespaced {}
