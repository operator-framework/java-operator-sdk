package io.javaoperatorsdk.operator.workflow.workflowactivationcondition;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("wac")
public class WorkflowActivationConditionCustomResource
    extends CustomResource<WorkflowActivationConditionSpec, Void> implements Namespaced {}
