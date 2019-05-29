package jkube.operator.sample;

import jkube.operator.Context;
import jkube.operator.api.CustomResourceController;

public class BitbucketController implements CustomResourceController<BitbucketRepository> {

    @Override
    public void deleteResource(BitbucketRepository resource, Context<BitbucketRepository> context) {

    }

    @Override
    public BitbucketRepository createOrUpdateResource(BitbucketRepository resource, Context<BitbucketRepository> context) {
        return null;
    }
}
