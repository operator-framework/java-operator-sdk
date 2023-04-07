package io.javaoperatorsdk.operator.performance;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("io.javaoperatorsdk.test")
@Version("v1")
@ShortNames("tc")
public class Counter extends CustomResource<CounterSpec, CounterStatus> implements Namespaced {

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.JSON_STYLE);
  }

}
