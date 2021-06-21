package io.javaoperatorsdk.operator;

public class MissingCRDException extends OperatorException {
  private final String crdName;
  private final String specVersion;

  public String getCrdName() {
    return crdName;
  }

  public String getSpecVersion() {
    return specVersion;
  }

  public MissingCRDException(String crdName, String specVersion) {
    super();
    this.crdName = crdName;
    this.specVersion = specVersion;
  }

  public MissingCRDException(String crdName, String specVersion, String message) {
    super(message);
    this.crdName = crdName;
    this.specVersion = specVersion;
  }

  public MissingCRDException(String crdName, String specVersion, String message, Throwable cause) {
    super(message, cause);
    this.crdName = crdName;
    this.specVersion = specVersion;
  }
}
