package io.javaoperatorsdk.operator.sample.eventsourcebyannotation.observedgeneration;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("esa")
public class EventSourceByAnnotationCustomResource
    extends CustomResource<Void, EventSourceByAnnotationCustomResourceStatus>
    implements Namespaced {

}
