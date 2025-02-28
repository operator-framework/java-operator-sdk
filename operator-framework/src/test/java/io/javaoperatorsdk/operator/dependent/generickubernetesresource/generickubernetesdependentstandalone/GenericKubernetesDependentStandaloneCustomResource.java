package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("gkd")
public class GenericKubernetesDependentStandaloneCustomResource
    extends CustomResource<GenericKubernetesDependentSpec, Void> implements Namespaced {}
