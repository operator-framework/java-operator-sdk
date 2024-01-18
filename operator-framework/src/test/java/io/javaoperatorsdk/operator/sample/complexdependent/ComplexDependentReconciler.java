package io.javaoperatorsdk.operator.sample.complexdependent;

import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.sample.complexdependent.dependent.*;

import static io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentReconciler.SERVICE_EVENT_SOURCE_NAME;
import static io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentReconciler.STATEFUL_SET_EVENT_SOURCE_NAME;

@ControllerConfiguration(
    name = "project-operator",
    workflow = @Workflow(dependents = {
        @Dependent(name = "first-svc", type = FirstService.class,
            useEventSourceWithName = SERVICE_EVENT_SOURCE_NAME),
        @Dependent(name = "second-svc", type = SecondService.class,
            useEventSourceWithName = SERVICE_EVENT_SOURCE_NAME),
        @Dependent(name = "first", type = FirstStatefulSet.class,
            useEventSourceWithName = STATEFUL_SET_EVENT_SOURCE_NAME,
            dependsOn = {"first-svc"},
            readyPostcondition = StatefulSetReadyCondition.class),
        @Dependent(name = "second",
            type = SecondStatefulSet.class,
            useEventSourceWithName = STATEFUL_SET_EVENT_SOURCE_NAME,
            dependsOn = {"second-svc", "first"},
            readyPostcondition = StatefulSetReadyCondition.class),
    }))
public class ComplexDependentReconciler implements Reconciler<ComplexDependentCustomResource>,
    EventSourceInitializer<ComplexDependentCustomResource> {

  public static final String SERVICE_EVENT_SOURCE_NAME = "serviceEventSource";
  public static final String STATEFUL_SET_EVENT_SOURCE_NAME = "statefulSetEventSource";

  @Override
  public UpdateControl<ComplexDependentCustomResource> reconcile(
      ComplexDependentCustomResource resource,
      Context<ComplexDependentCustomResource> context) throws Exception {
    var ready = context.managedDependentResourceContext().getWorkflowReconcileResult()
        .allDependentResourcesReady();

    var status = Objects.requireNonNullElseGet(resource.getStatus(), ComplexDependentStatus::new);
    status.setStatus(ready ? RECONCILE_STATUS.READY : RECONCILE_STATUS.NOT_READY);
    resource.setStatus(status);

    return UpdateControl.updateStatus(resource);
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ComplexDependentCustomResource> context) {
    InformerEventSource<Service, ComplexDependentCustomResource> serviceEventSource =
        new InformerEventSource<>(InformerConfiguration.from(Service.class, context).build(),
            context);
    InformerEventSource<StatefulSet, ComplexDependentCustomResource> statefulSetEventSource =
        new InformerEventSource<>(InformerConfiguration.from(StatefulSet.class, context).build(),
            context);
    return Map.of(SERVICE_EVENT_SOURCE_NAME, serviceEventSource, STATEFUL_SET_EVENT_SOURCE_NAME,
        statefulSetEventSource);
  }

  public enum RECONCILE_STATUS {
    READY, NOT_READY
  }
}
