package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("tomcatoperator.io")
@Version("v1")
public class Webapp extends CustomResource<WebappSpec, WebappStatus> {}
