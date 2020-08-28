# ![java-operator-sdk](docs/assets/images/logo.png) 
![Java CI with Maven](https://github.com/ContainerSolutions/java-operator-sdk/workflows/Java%20CI%20with%20Maven/badge.svg)

Build Kubernetes Operators in Java without hassle. Inspired by [operator-sdk](https://github.com/operator-framework/operator-sdk).

#### Features
* Framework for handling Kubernetes API events
* Registering Custom Resource watches
* Retry action on failure
* Smart event scheduling (only handle latest event for the same resource)

Check out this [blog post](https://blog.container-solutions.com/a-deep-dive-into-the-java-operator-sdk) 
about the non-trivial yet common problems needs to be solved for every operator. 

#### Why build your own Operator?
* Infrastructure automation using the power and flexibility of Java. See [blog post](https://blog.container-solutions.com/cloud-native-java-infrastructure-automation-with-kubernetes-operators).
* Provisioning of complex applications - avoiding Helm chart hell
* Integration with Cloud services - e.g. Secret stores
* Safer deployment of applications - only expose cluster to users by Custom Resources

#### Roadmap
* Testing of the framework and all samples while running on a real cluster.
* Generate a project skeleton
* Generate Java classes from CRD definion (and/or the other way around)
* Integrate with Quarkus (including native image build)
* Integrate with OLM (Operator Lifecycle Manager)

#### Join us on Discord!

[Discord Invite Link](https://discord.gg/DacEhAy)

#### User Guide

You can (will) find detailed documentation [here](docs/DOCS.md). 
Note that these docs are currently in progress. 

#### Usage

We have several sample Operators under the samples directory:
* *basic*: Minimal Operator implementation which only parses the Custom Resource and prints to stdout.
Implemented with and without Spring Boot support. The two samples share the common module.
* *webserver*: More realistic example creating an nginx webserver from a Custom Resource containing html code.
* *mysql-schema*: Operator managing schemas in a MySQL database
* *spring-boot-plain/auto-config*: Samples showing integration with Spring Boot.

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

#### Spring Boot

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
