# jkube-operator-sdk

SDK for building Kubernetes Operators in Java. Inspired by [operator-sdk](https://github.com/operator-framework/operator-sdk).
In this first iteration we aim to provide a framework which handles the reconciliation loop by dispatching events to
a Controller written by the user of the framework.

The Controller only contains the logic to create, update and delete the actual resources related to the CRD.

## Implementation

This library relies on the amazing [kubernetes-client]() from fabric8. Most of the heavy lifting is actually done by
kubernetes-client.

## Roadmap

Feature we would like to implement and invite the community to help us implement in the future:
* Testing support
* Class generation from CRD to POJO

## Usage

Main method initializing the Operator and registering a controller for Bitbucket.

```java
public static void main(String[] args) {
    Operator operator = Operator.initializeFromEnvironment();
    operator.registerController(new BitbucketController(new Bitbucket()));
}
```

The Controller implements the business logic and describes all the classes needed to handle the CRD.

```java
public class BitbucketRepoController implements CustomResourceController<BitbucketRepo, BitbucketRepoList, DoneableBitbucketRepo> {

    private final static Logger log = LoggerFactory.getLogger(BitbucketRepoService.class);

    // This class would handle the actual Bitbucket API calls
    private final Bitbucket bitbucket;

    public BitbucketRepoController(Bitbucket bitbucket) {
        this.bitbucket = bitbucket;
    }

    // Two methods containing the logic

    @Override
    public BitbucketRepo createOrUpdateResource(BitbucketRepo BitbucketRepo) {
        String url = bitbucket.createBitbucketRepository(BitbucketRepo.getSpec().getTeam(), BitbucketRepo.getSpec().getEnvironment());

        status.setRepoUrl(url);
        status.setState(BitbucketRepoState.CREATED);
        return BitbucketRepo;
    }

    @Override
    public void deleteResource(BitbucketRepo BitbucketRepo) {
        bitbucket.deleteBitbucketRepository(BitbucketRepo.getSpec().getTeam(), BitbucketRepo.getSpec().getEnvironment());
        log.info("Deleting Bitbucket repositor with name: {}", BitbucketRepo.getSpec().getTeam());
    }
    
    // Methods describing the CRD this Controller is handling

    @Override
    public Class<BitbucketRepo> getCustomResourceClass() {
        return BitbucketRepo.class;
    }

    @Override
    public Class<BitbucketRepoList> getCustomResourceListClass() {
        return BitbucketRepoList.class;
    }

    @Override
    public Class<DoneableBitbucketRepo> getCustomResourceDoneableClass() {
        return DoneableBitbucketRepo.class;
    }

    @Override
    public String getApiVersion() {
        return BitbucketRepo.API_VERSION;
    }

    @Override
    public String getCrdVersion() {
        return BitbucketRepo.CRD_VERSION;
    }
}

```

Classes mapping the Customer resource + boilerplate needed for the kubernetes-client.

```java
public class BitbucketRepo extends io.fabric8.kubernetes.client.CustomResource {
    public static final String CRD_VERSION = "v1";
    public static final String API_VERSION = "jkube.bitbucket/v1";
    public static final String DEFAULT_DELETE_FINALIZER = "finalizer.BitbucketRepo.jkube.bitbucket";

    private BitbucketRepoSpec spec;
    private BitbucketRepoStatus status;

    public BitbucketRepoSpec getSpec() {
        return spec;
    }

    public void setSpec(BitbucketRepoSpec spec) {
        this.spec = spec;
    }

    public BitbucketRepoStatus getStatus() {
        return status;
    }

    public void setStatus(BitbucketRepoStatus status) {
        this.status = status;
    }
}

public class BitbucketRepoList extends io.fabric8.kubernetes.client.CustomResourceList<BitbucketRepo> {
}

public class DoneableBitbucketRepo extends io.fabric8.kubernetes.client.CustomResourceDoneable<BitbucketRepo> {
    public DoneableBitbucketRepo(BitbucketRepo resource, io.fabric8.kubernetes.api.builder.Function function) {
        super(resource, function);
    }
}
```
