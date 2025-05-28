package io.javaoperatorsdk.operator.sample;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/** Represents a web application deployed in a Tomcat deployment */
@Group("tomcatoperator.io")
@Version("v1")
public class Webapp extends CustomResource<WebappSpec, WebappStatus> implements Namespaced {

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }
}
