package io.javaoperatorsdk.operator.workflow.complexdependent;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("cdc")
public class ComplexWorkflowCustomResource
    extends CustomResource<ComplexWorkflowSpec, ComplexWorkflowStatus> implements Namespaced {}
