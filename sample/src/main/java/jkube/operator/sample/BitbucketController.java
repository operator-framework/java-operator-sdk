package jkube.operator.sample;

import jkube.operator.Context;
import jkube.operator.api.Controller;
import jkube.operator.api.ResourceController;

@Controller(customResourceClass = BitbucketRepository.class,
        customResourceDefinitionName = BitbucketController.BITBUCKET_REPOSITORY_CRD_NAME)
public class BitbucketController implements ResourceController<BitbucketRepository> {

    public static final String BITBUCKET_REPOSITORY_CRD_NAME = "bitbucketRepository";

    @Override
    public void deleteResource(BitbucketRepository resource, Context<BitbucketRepository> context) {

    }

    @Override
    public BitbucketRepository createOrUpdateResource(BitbucketRepository resource, Context<BitbucketRepository> context) {
        return null;
    }
}
