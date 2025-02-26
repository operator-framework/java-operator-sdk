package io.javaoperatorsdk.operator.dependent.dependentssa;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("dssa")
public class DependentSSACustomResource extends CustomResource<DependentSSASpec, Void>
    implements Namespaced {}
