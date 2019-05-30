package jkube.operator.sample;

import jkube.operator.Context;
import jkube.operator.api.ResourceController;

public class BitbucketController implements ResourceController<BitbucketRepository> {

    @Override
    public void deleteResource(BitbucketRepository resource, Context<BitbucketRepository> context) {

    }

    @Override
    public BitbucketRepository createOrUpdateResource(BitbucketRepository resource, Context<BitbucketRepository> context) {
        return null;
    }
}
