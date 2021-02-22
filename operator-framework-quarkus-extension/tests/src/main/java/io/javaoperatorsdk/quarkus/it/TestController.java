package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.Controller;

@Controller(name = TestController.NAME)
public class TestController extends AbstractController<ChildTestResource> {
  public static final String NAME = "test";
}
