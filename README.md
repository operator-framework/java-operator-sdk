# java-operator-sdk
> This project is in incubation phase.

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
* Spring Boot support

## Usage
> Under sample directory you can try out our example.

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
        // ...  
    }

    @Override
    public CustomService createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        // ...
        return resource;
    }
}
```

Class that reflects CRD properties.

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
/