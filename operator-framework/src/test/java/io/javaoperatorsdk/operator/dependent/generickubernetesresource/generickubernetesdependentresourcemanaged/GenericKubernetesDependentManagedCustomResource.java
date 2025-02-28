package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentresourcemanaged;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("gkdm")
public class GenericKubernetesDependentManagedCustomResource
    extends CustomResource<GenericKubernetesDependentSpec, Void> implements Namespaced {}
