package io.javaoperatorsdk.operator.workflow.orderedmanageddependent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("OrderedManagedDependentCustomResource")
@ShortNames("omd")
public class OrderedManagedDependentCustomResource extends CustomResource<Void, String>
    implements Namespaced {}
