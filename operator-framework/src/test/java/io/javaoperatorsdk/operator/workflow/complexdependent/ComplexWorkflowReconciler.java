package io.javaoperatorsdk.operator.workflow.complexdependent;

import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.FirstService;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.FirstStatefulSet;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.SecondService;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.SecondStatefulSet;
import io.javaoperatorsdk.operator.workflow.complexdependent.dependent.StatefulSetReadyCondition;

import static io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowReconciler.SERVICE_EVENT_SOURCE_NAME;
import static io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowReconciler.STATEFUL_SET_EVENT_SOURCE_NAME;

@Workflow(
    dependents = {
      @Dependent(
          name = "first-svc",
          type = FirstService.class,
          useEventSourceWithName = SERVICE_EVENT_SOURCE_NAME),
      @Dependent(
          name = "second-svc",
          type = SecondService.class,
          useEventSourceWithName = SERVICE_EVENT_SOURCE_NAME),
      @Dependent(
          name = "first",
          type = FirstStatefulSet.class,
          useEventSourceWithName = STATEFUL_SET_EVENT_SOURCE_NAME,
          dependsOn = {"first-svc"},
          readyPostcondition = StatefulSetReadyCondition.class),
      @Dependent(
          name = "second",
          type = SecondStatefulSet.class,
          useEventSourceWithName = STATEFUL_SET_EVENT_SOURCE_NAME,
          dependsOn = {"second-svc", "first"},
          readyPostcondition = StatefulSetReadyCondition.class),
    })
@ControllerConfiguration(name = "project-operator")
public class ComplexWorkflowReconciler implements Reconciler<ComplexWorkflowCustomResource> {

  public static final String SERVICE_EVENT_SOURCE_NAME = "serviceEventSource";
  public static final String STATEFUL_SET_EVENT_SOURCE_NAME = "statefulSetEventSource";

  @Override
  public UpdateControl<ComplexWorkflowCustomResource> reconcile(
      ComplexWorkflowCustomResource resource, Context<ComplexWorkflowCustomResource> context)
      throws Exception {
    var ready =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow()
            .allDependentResourcesReady();

    var status = Objects.requireNonNullElseGet(resource.getStatus(), ComplexWorkflowStatus::new);
    status.setStatus(ready ? RECONCILE_STATUS.READY : RECONCILE_STATUS.NOT_READY);
    resource.setStatus(status);

    return UpdateControl.patchStatus(resource);
  }

  @Override
  public List<EventSource<?, ComplexWorkflowCustomResource>> prepareEventSources(
      EventSourceContext<ComplexWorkflowCustomResource> context) {
    InformerEventSource<Service, ComplexWorkflowCustomResource> serviceEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    Service.class, ComplexWorkflowCustomResource.class)
                .withName(SERVICE_EVENT_SOURCE_NAME)
                .build(),
            context);
    InformerEventSource<StatefulSet, ComplexWorkflowCustomResource> statefulSetEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    StatefulSet.class, ComplexWorkflowCustomResource.class)
                .withName(STATEFUL_SET_EVENT_SOURCE_NAME)
                .build(),
            context);
    return List.of(serviceEventSource, statefulSetEventSource);
  }

  public enum RECONCILE_STATUS {
    READY,
    NOT_READY
  }
}
