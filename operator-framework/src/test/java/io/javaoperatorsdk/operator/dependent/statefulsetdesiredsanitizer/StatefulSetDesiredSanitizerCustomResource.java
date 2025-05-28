package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
public class StatefulSetDesiredSanitizerCustomResource
    extends CustomResource<StatefulSetDesiredSanitizerSpec, Void> implements Namespaced {}
