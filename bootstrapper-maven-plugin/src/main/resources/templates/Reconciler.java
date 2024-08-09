package {{groupId}};

import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;

import java.util.Map;
import java.util.Optional;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
public class {{artifactClassId}}Reconciler implements Reconciler<{{artifactClassId}}CustomResource> {

    public UpdateControl<{{artifactClassId}}CustomResource> reconcile({{artifactClassId}}CustomResource primary,
                                                     Context<{{artifactClassId}}CustomResource> context) {

        return UpdateControl.noUpdate();
    }
}
