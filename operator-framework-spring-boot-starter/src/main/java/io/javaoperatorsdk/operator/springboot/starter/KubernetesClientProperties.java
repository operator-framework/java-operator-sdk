package io.javaoperatorsdk.operator.springboot.starter;

import java.util.Optional;

public class KubernetesClientProperties {

  private boolean openshift = false;
  private String username;
  private String password;
  private String masterUrl;
  private boolean trustSelfSignedCertificates = false;

  public boolean isOpenshift() {
    return openshift;
  }

  public KubernetesClientProperties setOpenshift(boolean openshift) {
    this.openshift = openshift;
    return this;
  }

  public Optional<String> getUsername() {
    return Optional.ofNullable(username);
  }

  public KubernetesClientProperties setUsername(String username) {
    this.username = username;
    return this;
  }

  public Optional<String> getPassword() {
    return Optional.ofNullable(password);
  }

  public KubernetesClientProperties setPassword(String password) {
    this.password = password;
    return this;
  }

  public Optional<String> getMasterUrl() {
    return Optional.ofNullable(masterUrl);
  }

  public KubernetesClientProperties setMasterUrl(String masterUrl) {
    this.masterUrl = masterUrl;
    return this;
  }

  public boolean isTrustSelfSignedCertificates() {
    return trustSelfSignedCertificates;
  }

  public KubernetesClientProperties setTrustSelfSignedCertificates(
      boolean trustSelfSignedCertificates) {
    this.trustSelfSignedCertificates = trustSelfSignedCertificates;
    return this;
  }
}
