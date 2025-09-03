package io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("aecs")
public class PropagateAllEventCustomResource extends CustomResource<PropagateAllEventSpec, Void>
    implements Namespaced {}
