package io.javaoperatorsdk.quarkus.it;

import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("example.com")
@Version("v1")
public class ChildTestResource extends TestResource {}
