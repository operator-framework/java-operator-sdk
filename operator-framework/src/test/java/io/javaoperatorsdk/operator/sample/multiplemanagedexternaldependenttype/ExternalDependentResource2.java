package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

public class ExternalDependentResource2 extends AbstractExternalDependentResource {

  public static final String SUFFIX = "-2";

  @Override
  protected String resourceIDSuffix() {
    return SUFFIX;
  }
}
