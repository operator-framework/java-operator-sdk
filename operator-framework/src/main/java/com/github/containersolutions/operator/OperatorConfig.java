package com.github.containersolutions.operator;

import org.apache.commons.lang3.StringUtils;

public class OperatorConfig {

    private boolean openshift = false;
    private String username;
    private String password;
    private String masterUrl;
    private boolean trustSelfSignedCertificates = false;

    public OperatorConfig initFromEnvironment() {
        if (StringUtils.isNotBlank(System.getenv("K8S_MASTER_URL"))) {
            setMasterUrl(System.getenv("K8S_MASTER_URL"));
        }
        if (StringUtils.isNoneBlank(System.getenv("K8S_USERNAME"), System.getenv("K8S_PASSWORD"))) {
            setUsername(System.getenv("K8S_USERNAME"));
            setPassword(System.getenv("K8S_PASSWORD"));
        }
        return this;
    }

    public boolean isOpenshift() {
        return openshift;
    }

    public OperatorConfig setOpenshift(boolean openshift) {
        this.openshift = openshift;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public OperatorConfig setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public OperatorConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates;
    }

    public OperatorConfig setTrustSelfSignedCertificates(boolean trustSelfSignedCertificates) {
        this.trustSelfSignedCertificates = trustSelfSignedCertificates;
        return this;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public OperatorConfig setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        return this;
    }
}
