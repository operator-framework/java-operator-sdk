package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ControllerConfiguration
public class NamespaceDeletionRoleReconciler implements Reconciler<Role>, Cleaner<Role>, EventSourceInitializer<Role> {

  List<Class<? extends HasMetadata>> resourceClasses;

  public NamespaceDeletionRoleReconciler(List<Class<? extends HasMetadata>> resourceClasses) {
    this.resourceClasses = resourceClasses;
  }

  @Override
  public UpdateControl<Role> reconcile(Role resource, Context<Role> context) throws Exception {
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Role resource, Context<Role> context) {
    return null;
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Role> context) {

    return  resourceClasses.stream().map(c-> new InformerEventSource(InformerConfiguration.from(c,context)
            .withPrimaryToSecondaryMapper(primary -> {

                 return null;
            })
            .withSecondaryToPrimaryMapper((SecondaryToPrimaryMapper) resource -> null)
            .build(),context)).collect(Collectors.toMap(i->i.resourceType().getName(),i->i));

  }
}
