package io.javaoperatorsdk.operator.workflow.workflowmultipleactivation;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("mwac")
public class WorkflowMultipleActivationCustomResource
    extends CustomResource<WorkflowMultipleActivationSpec, Void> implements Namespaced {}
