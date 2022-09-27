package io.javaoperatorsdk.operator.sample.multiplemanagedexternaldependenttype;

public class ExternalDependentResource2 extends AbstractExternalDependentResource {

  public static final String SUFFIX = "-2";

  public ExternalDependentResource2() {
    setResourceDiscriminator(new ExternalResourceDiscriminator(SUFFIX));
  }

  @Override
  protected String resourceIDSuffix() {
    return SUFFIX;
  }
}
