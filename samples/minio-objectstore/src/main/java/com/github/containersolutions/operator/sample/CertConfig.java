package com.github.containersolutions.operator.sample;

import java.util.List;

public class CertConfig {
    private String commonName;
    private List<String> organizationName = null;
    private List<String> dnsNames = null;

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public List<String> getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(List<String> organizationName) {
        this.organizationName = organizationName;
    }

    public List<String> getDnsNames() {
        return dnsNames;
    }

    public void setDnsNames(List<String> dnsNames) {
        this.dnsNames = dnsNames;
    }
}
