package {{groupId}};

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Map;
import java.util.Optional;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
@ControllerConfiguration
public class {{artifactClassId}}Reconciler implements Reconciler<{{artifactClassId}}CustomResource> {

    public UpdateControl<{{artifactClassId}}CustomResource> reconcile({{artifactClassId}}CustomResource primary,
                                                     Context<{{artifactClassId}}CustomResource> context) {

        return UpdateControl.noUpdate();
    }
}
