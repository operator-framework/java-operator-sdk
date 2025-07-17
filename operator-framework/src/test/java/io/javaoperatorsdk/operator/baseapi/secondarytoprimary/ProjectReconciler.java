package io.javaoperatorsdk.operator.baseapi.secondarytoprimary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration()
public class ProjectReconciler implements Reconciler<Project> {
    private static final Logger log = LoggerFactory.getLogger(ProjectReconciler.class);

    private Integer memberCount;

    ProjectReconciler(Integer memberCount) {
        super();
        this.memberCount = memberCount;
    }

    private final AtomicInteger firstTimeSecondaryResourceCount = new AtomicInteger(-1);
    private final AtomicInteger firstTimeSecondaryResourceCountFromCache = new AtomicInteger(-1);

    @Override
    public UpdateControl<Project> reconcile(Project resource, Context<Project> context) {

        var secondaryResources = context
                .getSecondaryResources(Member.class);
        firstTimeSecondaryResourceCount.compareAndSet(-1, secondaryResources.size());

        var informerEventSource =
                (InformerEventSource<Member, Project>)
                        context.eventSourceRetriever().getEventSourceFor(Member.class);
        var cacheResources = informerEventSource.list().filter(m -> m.getSpec().getProjectName().equals(resource.getMetadata().getName()));
        var cacheSize = cacheResources.toList().size();
        firstTimeSecondaryResourceCountFromCache.compareAndSet(-1, cacheSize);
        log.info("Expected {} secondary resources, got {} from getSecondaryResources, got {} from cache", this.memberCount, secondaryResources.size(), cacheSize);

        return UpdateControl.noUpdate();
    }

    @Override
    public List<EventSource<?, Project>> prepareEventSources(EventSourceContext<Project> context) {

        InformerEventSourceConfiguration.Builder<Member> informerConfiguration =
                InformerEventSourceConfiguration.from(Member.class, Project.class)
                        .withSecondaryToPrimaryMapper(
                                member ->
                                        Set.of(
                                                new ResourceID(
                                                        member.getSpec().getProjectName(),
                                                        member.getMetadata().getNamespace())))
                        .withNamespacesInheritedFromController();

        return List.of(new InformerEventSource<>(informerConfiguration.build(), context));
    }

    public int getFirstTimeSecondaryResourceCount() {
        return firstTimeSecondaryResourceCount.get();
    }

    public int getFirstTimeSecondaryResourceCountFromCache() {
        return firstTimeSecondaryResourceCountFromCache.get();
    }

    @Override
    public ErrorStatusUpdateControl<Project> updateErrorStatus(
            Project resource, Context<Project> context, Exception e) {
        return ErrorStatusUpdateControl.noStatusUpdate();
    }
}
