package io.javaoperatorsdk.operator.dependent.readonly;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1")
@Group("josdk.io")
public class ConfigMapReader extends CustomResource<Void, String> implements Namespaced {}
