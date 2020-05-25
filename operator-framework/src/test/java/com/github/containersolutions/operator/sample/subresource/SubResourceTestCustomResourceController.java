package com.github.containersolutions.operator.sample.subresource;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.CustomResourceClientAware;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Controller(
        crdName = SubResourceTestCustomResourceController.CRD_NAME,
        customResourceClass = SubResourceTestCustomResource.class)
public class SubResourceTestCustomResourceController implements ResourceController<SubResourceTestCustomResource>, CustomResourceClientAware<SubResourceTestCustomResource> {

    public static final String CRD_NAME = "customservices2.sample.javaoperatorsdk";
    private static final Logger log = LoggerFactory.getLogger(SubResourceTestCustomResourceController.class);
    private MixedOperation<SubResourceTestCustomResource, KubernetesResourceList<SubResourceTestCustomResource>,
            Doneable<SubResourceTestCustomResource>, Resource<SubResourceTestCustomResource, Doneable<SubResourceTestCustomResource>>> customResourceClient;
    private AtomicInteger numberOfExecutions = new AtomicInteger(0);

    @Override
    public boolean deleteResource(SubResourceTestCustomResource resource) {
        return true;
    }

    @Override
    public Optional<SubResourceTestCustomResource> createOrUpdateResource(SubResourceTestCustomResource resource) {
        numberOfExecutions.addAndGet(1);
        log.info("Value: " + resource.getSpec().getValue());

        SubResourceTestCustomResourceStatus status = resource.getStatus();
        if (status == null) {
            status = new SubResourceTestCustomResourceStatus();
            resource.setStatus(status);
        }
        status.setState(SubResourceTestCustomResourceStatus.State.SUCCESS);
        customResourceClient.updateStatus(resource);
        return Optional.empty();
    }

    @Override
    public void setCustomResourceClient(MixedOperation<SubResourceTestCustomResource, KubernetesResourceList<SubResourceTestCustomResource>,
            Doneable<SubResourceTestCustomResource>, Resource<SubResourceTestCustomResource, Doneable<SubResourceTestCustomResource>>> client) {
        this.customResourceClient = client;
    }
}
