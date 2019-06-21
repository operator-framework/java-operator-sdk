# java-operator-sdk
> This project is in incubation phase.

SDK for building Kubernetes Operators in Java. Inspired by [operator-sdk](https://github.com/operator-framework/operator-sdk).
In this first iteration we aim to provide a framework which handles the reconciliation loop by dispatching events to
a Controller written by the user of the framework.

The Controller only contains the logic to create, update and delete the actual resources related to the CRD.

## Implementation

This library relies on the amazing [kubernetes-client](https://github.com/fabric8io/kubernetes-client) from fabric8. Most of the heavy lifting is actually done by
kubernetes-client.

## Roadmap

Feature we would like to implement and invite the community to help us implement in the future:

* ~~Spring Boot support~~
* Testing support
* Class generation from CRD to POJO


## Usage
> Under sample directory you can try out our sample.

Add dependency to your project:

```xml
<dependency>
  <groupId>com.github.containersolutions</groupId>
  <artifactId>operator-framework</artifactId>
  <version>0.1.1</version>
</dependency>
```

Main method initializing the Operator and registering a controller..

```java
public class Runner {

   public static void main(String[] args) {
       Operator operator = new Operator(new OperatorConfig().setTrustSelfSignedCertificates(true));
       operator.registerController(new CustomServiceController());
   }
}
```

The Controller implements the business logic and describes all the classes needed to handle the CRD.

```java
@Controller(customResourceClass = CustomService.class,
        kind = CustomServiceController.CRD_NAME,
        group = CustomServiceController.GROUP,
        customResourceListClass = CustomServiceList.class,
        customResourceDonebaleClass = CustomServiceDoneable.class)
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String CRD_NAME = "CustomService";
    public static final String GROUP = "sample.javaoperatorsdk";

    @Override
    public void deleteResource(CustomService resource, Context<CustomService> context) {
        // ... your logic ...
    }
    
    // Return the changed resource, so it gets updated. See javadoc for details.
    @Override
    public Optional<CustomService> createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        // ... your logic ...
        return resource;
    }
}
```

Our custom resource java representation

```java
public class CustomService extends CustomResource {

    private ServiceSpec spec;

    public ServiceSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }
}
```

## Spring Boot Support

We provide a spring boot starter to automatically handle bean registration, and registering variouse components as beans. 
To use it just include the following dependency to you project: 

```
<dependency>
 <groupId>com.github.containersolutions</groupId>
 <artifactId>spring-boot-operator-framework-starter</artifactId>
 <version>[version]</version>
</dependency>
```

Note that controllers needs to be registered as a bean. Thus just annotating them also with `@Component` annotation.
See Spring docs for for details, also our sample with component scanning. 
All controllers that are registered as a bean, gets automatically registered to operator. 
 
Kubernetes client creation using properties is also supported, for complete list see: [Link for config class]  

## Dealing with Consistency 

### Run Single Instance

There should be always just one instance of an operator running at a time (think process). If there there would be 
two ore more, in general it could lead to concurrency issues. Note that we are also doing optimistic locking when we update a resource.
In this way the operator is not highly available. However for operators this not necessary an issue, 
if the operator just gets restarted after it went down. 

### Operator Restarts

When an operator is started we got events for every resource (of a type we listen to) already on the cluster. Even if the resource is not changed 
(We use `kubectl get ... --watch` in the background). This can be a huge amount of resources depending on your use case.
So it could be a good case just have a status field on the resource which is checked, if there anything needs to be done.

### At Least Once

To implement controller logic, we have to override two methods: `createOrUpdateResource` and `deleteResource`. 
These methods are called if a resource is create/changed or marked for deletion. In most cases these methods will be
called just once, but in some rare cases can happen that are called more then once. In practice this means that the 
implementation needs to be **idempotent**.    

### Deleting a Resource

During deletion process we use [Kubernetes finalizers](https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#finalizers 
"Kubernetes docs") finalizers. This is required, since it can happen that the operator is not running while the delete 
of resource is executed (think `oc delete`). In this case we would not catch the delete event. So we automatically add a
finalizer first time we update the resource if its not there. 
