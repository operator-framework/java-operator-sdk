package io.javaoperatorsdk.operator.baseapi.labelselector;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("lst")
public class LabelSelectorTestCustomResource extends CustomResource<Void, Void>
    implements Namespaced {}
