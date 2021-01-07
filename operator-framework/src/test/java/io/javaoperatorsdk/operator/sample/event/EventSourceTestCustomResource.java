package io.javaoperatorsdk.operator.sample.event;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class EventSourceTestCustomResource
    extends CustomResource<
        EventSourceTestCustomResourceSpec, EventSourceTestCustomResourceStatus> {}
