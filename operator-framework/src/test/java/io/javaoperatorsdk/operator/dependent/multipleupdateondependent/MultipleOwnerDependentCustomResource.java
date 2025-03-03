package io.javaoperatorsdk.operator.dependent.multipleupdateondependent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("mod")
public class MultipleOwnerDependentCustomResource
    extends CustomResource<MultipleOwnerDependentSpec, Void> implements Namespaced {}
