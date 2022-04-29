package io.javaoperatorsdk.operator.api;

import io.javaoperatorsdk.operator.processing.Controller;

public class RegisteredController {

  private Controller<?> controller;

  public RegisteredController(Controller<?> controller) {
    this.controller = controller;
  }

  public void changeNamespaces(String... namespaces) {
    controller.changeNamespaces(namespaces);
  }
}
