# java-operator-sdk
![Java CI with Maven](https://github.com/ContainerSolutions/java-operator-sdk/workflows/Java%20CI%20with%20Maven/badge.svg)

SDK for building Kubernetes Operators in Java. Inspired by [operator-sdk](https://github.com/operator-framework/operator-sdk).
User (you) only writes the logic in a Controller that creates/updates or deletes resources related to a custom resource.
All the issues around are handled by the framework for you.
Check out this [blog post](https://blog.container-solutions.com/a-deep-dive-into-the-java-operator-sdk) 
about the non-trivial yet common problems needs to be solved for every operator. 

## Join us on Discord!

[Discord Invite Link](https://discord.gg/DacEhAy)

## Roadmap

Feature we would like to implement and invite the community to help us implement in the future:

* ~~Spring Boot support~~
* Class generation from CRD to POJO
* GraalVM / Quarkus support

## Additional Features

* Configurable Retry Handling
* Smart Event Processing Scheduling

## User Guide

You can (will) find detailed documentation [here](docs/DOCS.md). 
Note that these docs are currently in progress. 

## Usage

We have several sample Operators under the samples directory:
* *basic*: Minimal Operator implementation which only parses the Custom Resource and prints to stdout.
Implemented with and without Spring Boot support. The two samples share the common module.
* *webserver*: More realistic example creating an nginx webserver from a Custom Resource containing html code.

Add [dependency](https://search.maven.org/search?q=a:operator-framework) to your project:

```xml
<dependency>
  <groupId>com.github.containersolutions</groupId>
  <artifactId>operator-framework</artifactId>
  <version>{see https://search.maven.org/search?q=a:operator-framework for latest version}</version>
</dependency>
```

Main method initializing the Operator and registering a controller..

```java
public class Runner {

   public static void main(String[] args) {
       Operator operator = new Operator(new DefaultKubernetesClient());
       operator.registerController(new WebServerController());
   }
}
```

The Controller implements the business logic and describes all the classes needed to handle the CRD.

```java
@Controller(customResourceClass = WebServer.class,
        crdName = "webservers.sample.javaoperatorsdk")
public class WebServerController implements ResourceController<WebServer> {

    @Override
    public boolean deleteResource(CustomService resource, Context<WebServer> context) {
        // ... your logic ...
        return true;
    }
    
    // Return the changed resource, so it gets updated. See javadoc for details.
    @Override
    public UpdateControl<CustomService> createOrUpdateResource(CustomService resource, Context<WebServer> context) {
        // ... your logic ...
        return UpdateControl.updateStatusSubResource(resource);
    }
}
```

A sample custom resource POJO representation

```java
public class WebServer extends CustomResource {

    private WebServerSpec spec;

    private WebServerStatus status;

    public WebServerSpec getSpec() {
        return spec;
    }

    public void setSpec(WebServerSpec spec) {
        this.spec = spec;
    }

    public WebServerStatus getStatus() {
        return status;
    }

    public void setStatus(WebServerStatus status) {
        this.status = status;
    }
}

public class WebServerSpec {

    private String html;

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }
}
```

### Spring Boot

You can also let Spring Boot wire your application together and automatically register the controllers.

Add [this dependency](https://search.maven.org/search?q=a:spring-boot-operator-framework-starter) to your project:

```xml
<dependency>
 <groupId>com.github.containersolutions</groupId>
 <artifactId>spring-boot-operator-framework-starter</artifactId>
 <version>{see https://search.maven.org/search?q=a:spring-boot-operator-framework-starter for latest version}</version>
</dependency>
```

Create an Application
```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

add Spring's `@Service` annotation to your controller classes so they will be automatically registered as resource controllers.

The Operator's Spring Boot integration leverages [Spring's configuration mechanisms](https://docs.spring.io/spring-boot/docs/1.0.1.RELEASE/reference/html/boot-features-external-config.html) to configure
- [The Kubernetes client](spring-boot-starter/src/main/java/com/github/containersolutions/operator/spingboot/starter/OperatorProperties.java)
- [Retries](spring-boot-starter/src/main/java/com/github/containersolutions/operator/spingboot/starter/RetryProperties.java)

## Implementation / Design details

This library relies on the amazing [kubernetes-client](https://github.com/fabric8io/kubernetes-client) from fabric8. 
Most of the heavy lifting is actually done by kubernetes-client.

What the framework adds on top of the bare client:
* Management of events from the Kubernetes API. All events are inserted into a queue by the EventScheduler. The 
framework makes sure only the latest event for a certain resource is processed. This is especially important since
on startup the operator can receive a whole series of obsolete events.
* Retrying of failed actions. When an event handler throws an exception the event is put back in the queue.
* A clean interface to the user of the framework to receive events related to the Controller's resource.

### Dealing with Consistency 

#### Run Single Instance

There should be always just one instance of an operator running at a time (think process). If there there would be 
two ore more, in general it could lead to concurrency issues. Note that we are also doing optimistic locking when we update a resource.
In this way the operator is not highly available. However for operators this not necessary an issue, 
if the operator just gets restarted after it went down. 

#### At Least Once

To implement controller logic, we have to override two methods: `createOrUpdateResource` and `deleteResource`. 
These methods are called if a resource is create/changed or marked for deletion. In most cases these methods will be
called just once, but in some rare cases can happen that are called more then once. In practice this means that the 
implementation needs to be **idempotent**.    

#### Smart Scheduling

In our scheduling algorithm we make sure, that no events are processed concurrently for a resource. In addition we provide
a customizable retry mechanism to deal with temporal errors.

#### Operator Restarts

When an operator is started we got events for every resource (of a type we listen to) already on the cluster. Even if the resource is not changed 
(We use `kubectl get ... --watch` in the background). This can be a huge amount of resources depending on your use case.
So it could be a good case just have a status field on the resource which is checked, if there anything needs to be done.

#### Deleting a Resource

During deletion process we use [Kubernetes finalizers](https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/custom-resource-definitions/#finalizers 
"Kubernetes docs") finalizers. This is required, since it can happen that the operator is not running while the delete 
of resource is executed (think `oc delete`). In this case we would not catch the delete event. So we automatically add a
finalizer first time we update the resource if its not there. 
