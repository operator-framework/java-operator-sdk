package io.javaoperatorsdk.operator.baseapi.multiplesecondaryeventsource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("MultipleSecondaryEventSourceCustomResource")
@ShortNames("mses")
public class MultipleSecondaryEventSourceCustomResource extends CustomResource<Void, Void>
    implements Namespaced {}
