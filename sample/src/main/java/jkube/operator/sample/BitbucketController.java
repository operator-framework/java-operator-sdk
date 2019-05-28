package jkube.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;
import jkube.operator.CustomResourceController;

public class BitbucketController implements CustomResourceController {

    @Override
    public void deleteResource(CustomResource resource) {

    }

    @Override
    public CustomResource createOrUpdateResource(CustomResource resource) {
        return null;
    }

    @Override
    public Class getCustomResourceClass() {
        return null;
    }

    @Override
    public Class getCustomResourceListClass() {
        return null;
    }

    @Override
    public Class getCustomResourceDoneableClass() {
        return null;
    }

    @Override
    public String getApiVersion() {
        return null;
    }

    @Override
    public String getCrdVersion() {
        return null;
    }
}
