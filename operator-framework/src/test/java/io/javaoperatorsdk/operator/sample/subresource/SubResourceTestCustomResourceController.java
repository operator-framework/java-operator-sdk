package io.javaoperatorsdk.operator.sample.subresource;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.TestExecutionInfoProvider;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller(
    crdName = SubResourceTestCustomResourceController.CRD_NAME,
    customResourceClass = SubResourceTestCustomResource.class,
    generationAwareEventProcessing = false)
public class SubResourceTestCustomResourceController implements ResourceController<SubResourceTestCustomResource>,
    TestExecutionInfoProvider {
    
    public static final String CRD_NAME = "subresourcesample.sample.javaoperatorsdk";
    public static final String FINALIZER_NAME = CRD_NAME + "/finalizer";
    private static final Logger log = LoggerFactory.getLogger(SubResourceTestCustomResourceController.class);
    private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
    
    @Override
    public boolean deleteResource(SubResourceTestCustomResource resource, Context<SubResourceTestCustomResource> context) {
        return true;
    }
    
    @Override
    public UpdateControl<SubResourceTestCustomResource> createOrUpdateResource(SubResourceTestCustomResource resource,
                                                                               Context<SubResourceTestCustomResource> context) {
        numberOfExecutions.addAndGet(1);
        if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
            throw new IllegalStateException("Finalizer is not present.");
        }
        log.info("Value: " + resource.getSpec().getValue());

        ensureStatusExists(resource);
        resource.getStatus().setState(SubResourceTestCustomResourceStatus.State.SUCCESS);

        return UpdateControl.updateStatusSubResource(resource);
    }

    private void ensureStatusExists(SubResourceTestCustomResource resource) {
        SubResourceTestCustomResourceStatus status = resource.getStatus();
        if (status == null) {
            status = new SubResourceTestCustomResourceStatus();
            resource.setStatus(status);
        }
    }

    public int getNumberOfExecutions() {
        return numberOfExecutions.get();
    }
}
