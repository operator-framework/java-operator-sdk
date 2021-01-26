package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.crd.CRD;

@Group("sample.javaoperatorsdk")
@Version("v1")
@CRD
public class CustomService extends CustomResource<ServiceSpec, Void> implements Namespaced {}
