package io.javaoperatorsdk.operator.sample.bulkdependent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("sbd")
public class BulkDependentTestCustomResource
    extends CustomResource<BulkDependentTestSpec, Void>
    implements Namespaced {
}
