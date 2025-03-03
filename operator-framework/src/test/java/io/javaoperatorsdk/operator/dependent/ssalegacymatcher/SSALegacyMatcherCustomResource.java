package io.javaoperatorsdk.operator.dependent.ssalegacymatcher;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("slm")
public class SSALegacyMatcherCustomResource extends CustomResource<SSALegacyMatcherSpec, Void>
    implements Namespaced {}
