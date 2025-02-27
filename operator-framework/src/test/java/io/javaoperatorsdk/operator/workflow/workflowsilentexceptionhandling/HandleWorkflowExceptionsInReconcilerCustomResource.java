package io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("hweir")
public class HandleWorkflowExceptionsInReconcilerCustomResource extends CustomResource<Void, Void>
    implements Namespaced {}
