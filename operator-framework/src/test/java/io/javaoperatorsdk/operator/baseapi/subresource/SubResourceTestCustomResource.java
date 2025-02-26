package io.javaoperatorsdk.operator.baseapi.subresource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("SubresourceSample")
@Plural("subresourcesample")
@ShortNames("ss")
public class SubResourceTestCustomResource
    extends CustomResource<SubResourceTestCustomResourceSpec, SubResourceTestCustomResourceStatus>
    implements Namespaced {}
