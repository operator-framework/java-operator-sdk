package io.javaoperatorsdk.operator.sample.customfilter;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.sample.doubleupdate.DoubleUpdateTestCustomResourceStatus;

@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("CustomFilteringTest")
@ShortNames("cft")
public class CustomFilteringTestResource
    extends CustomResource<CustomFilteringTestResourceSpec, Void>
    implements Namespaced {
}
