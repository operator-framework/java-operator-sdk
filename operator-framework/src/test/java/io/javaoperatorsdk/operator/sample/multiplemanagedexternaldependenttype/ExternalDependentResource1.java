package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

public class ExternalDependentResource1 extends AbstractExternalDependentResource {

  public static final String SUFFIX = "-1";

  @Override
  protected String resourceIDSuffix() {
    return SUFFIX;
  }
}
