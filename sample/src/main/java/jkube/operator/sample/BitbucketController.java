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

}
