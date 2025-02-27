package io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class CreateOnlyIfNotExistingDependentWithSSACustomResource
    extends CustomResource<Void, Void> implements Namespaced {}
