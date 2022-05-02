package io.javaoperatorsdk.operator.api;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.Controller;

public class RegisteredController {

  private Controller<?> controller;

  public RegisteredController(Controller<?> controller) {
    this.controller = controller;
  }

  public void changeNamespaces(Set<String> namespaces) {
    controller.changeNamespaces(namespaces);
  }

  public void changeNamespaces(String... namespaces) {
    changeNamespaces(Set.of(namespaces));
  }
}
