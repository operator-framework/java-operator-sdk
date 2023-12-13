package io.javaoperatorsdk.operator.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.fabric8.kubernetes.api.model.HasMetadata.getPlural;

// todo filters
@ControllerConfiguration
public class NamespaceDeletionRoleReconciler
    implements Reconciler<Role>, Cleaner<Role>, EventSourceInitializer<Role> {

  public static final String TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX = "target-resources-in-namespace";
  public static final String RESOURCE_NAMESPACE_INDEX = "resource-namespace-index";

  final List<Class<? extends HasMetadata>> resourceClasses;
  final Map<Class<? extends HasMetadata>, String> plurals;

  public NamespaceDeletionRoleReconciler(List<Class<? extends HasMetadata>> resourceClasses) {
    this.resourceClasses = resourceClasses;
    this.plurals =
        resourceClasses.stream().collect(Collectors.toMap(c -> c, HasMetadata::getPlural));
  }

  @Override
  public UpdateControl<Role> reconcile(Role resource, Context<Role> context) throws Exception {
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Role resource, Context<Role> context) {
      

    return DeleteControl.defaultDelete();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<Role> context) {
     var allPlurals = plurals.values();
     context.getPrimaryCache().addIndexer(TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX, r-> {
       // resource-plural+namespace -> role (in that namespace)
       List<String> result = new ArrayList<>();
       r.getRules().forEach(rule->{
         rule.getResources().forEach(resource-> {
           if (allPlurals.contains(resource)) {
              result.add(keyFor(r.getMetadata().getNamespace(),resource));
           }
         });
       });
       return result;
     });

    return resourceClasses.stream()
        .map(c -> { var ies =  new InformerEventSource<HasMetadata,Role>(InformerConfiguration.from(c, context)
            .withSecondaryToPrimaryMapper((SecondaryToPrimaryMapper) resource -> {
              HasMetadata rm = (HasMetadata) resource;
              var roles = context.getPrimaryCache().byIndex(TARGET_RESOURCES_IN_NAMESPACE_TO_ROLE_INDEX,
                      keyFor(rm.getMetadata().getNamespace(),rm.getPlural()));
              return roles.stream().map(r->new ResourceID(r.getMetadata().getName(),
                      r.getMetadata().getNamespace())).collect(Collectors.toSet());
            })
            .build(), context);
          ies.addIndexer(RESOURCE_NAMESPACE_INDEX, r-> List.of(r.getMetadata().getNamespace()));
          return ies;
        })
        .collect(Collectors.toMap(i -> plurals.get(i.resourceType()), i -> i));
  }

  public static String keyFor(String namespace, String resourcePlural) {
     return resourcePlural+"-"+namespace;
  }

}
