package io.javaoperatorsdk.operator;

import java.util.Set;

public interface RegisteredController {

  void changeNamespaces(Set<String> namespaces);

  void changeNamespaces(String... namespaces);

}
