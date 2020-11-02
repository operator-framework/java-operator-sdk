package com.github.containersolutions.operator.sample.subresource;

import com.github.containersolutions.operator.TestExecutionInfoProvider;
import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

@Controller(
        crdName = SubResourceTestCustomResourceController.CRD_NAME,
        customResourceClass = SubResourceTestCustomResource.class,
        generationAwareEventProcessing = false)
public class SubResourceTestCustomResourceController implements ResourceController<SubResourceTestCustomResource>,
        TestExecutionInfoProvider {

    public static final String CRD_NAME = "subresourcesample.sample.javaoperatorsdk";
    private static final Logger log = LoggerFactory.getLogger(SubResourceTestCustomResourceController.class);
    private AtomicInteger numberOfExecutions = new AtomicInteger(0);

    @Override
    public boolean deleteResource(SubResourceTestCustomResource resource, Context<SubResourceTestCustomResource> context) {
        return true;
    }

    @Override
    public UpdateControl<SubResourceTestCustomResource> createOrUpdateResource(SubResourceTestCustomResource resource,
                                                                               Context<SubResourceTestCustomResource> context) {
        numberOfExecutions.addAndGet(1);
        if (!resource.getMetadata().getFinalizers().contains(Controller.DEFAULT_FINALIZER)) {
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
