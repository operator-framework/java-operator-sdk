package io.javaoperatorsdk.operator.baseapi.multiversioncrd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version(value = "v2", storage = false)
@Kind("MultiVersionCRDTestCustomResource")
@ShortNames("mvc")
public class MultiVersionCRDTestCustomResource2
    extends CustomResource<
        MultiVersionCRDTestCustomResourceSpec2, MultiVersionCRDTestCustomResourceStatus2>
    implements Namespaced {}
