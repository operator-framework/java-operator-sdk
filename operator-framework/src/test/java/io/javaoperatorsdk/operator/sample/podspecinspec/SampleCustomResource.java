package io.javaoperatorsdk.operator.sample.podspecinspec;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("scr")
public class SampleCustomResource extends CustomResource<SampleSpec, Void> implements Namespaced {
}
