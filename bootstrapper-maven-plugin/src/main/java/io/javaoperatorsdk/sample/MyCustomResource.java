package io.javaoperatorsdk.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

// todo group
@Group("sample.javaoperatorsdk")
@Version("v1")
public class MyCustomResource extends CustomResource<MySpec,MyStatus> implements Namespaced {
}
