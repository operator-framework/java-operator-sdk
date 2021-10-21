package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("tomcatoperator.io")
@Version("v1")
public class Tomcat extends CustomResource<TomcatSpec, TomcatStatus> implements Namespaced {}
