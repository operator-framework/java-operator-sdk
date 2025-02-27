package io.javaoperatorsdk.operator.dependent.multiplemanagedexternaldependenttype;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceSpec;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("mme")
public class MultipleManagedExternalDependentResourceCustomResource
    extends CustomResource<MultipleManagedDependentResourceSpec, Void> implements Namespaced {}
