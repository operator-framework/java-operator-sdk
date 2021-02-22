package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.Controller;

@Controller(name = ConfiguredController.NAME, namespaces = "foo")
public class ConfiguredController extends AbstractController<ChildTestResource> {

  public static final String NAME = "annotation";
}
