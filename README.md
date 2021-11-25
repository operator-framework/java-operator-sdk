# ![java-operator-sdk](docs/assets/images/logo.png)

![Java CI with Maven](https://github.com/java-operator-sdk/java-operator-sdk/workflows/Java%20CI%20with%20Maven/badge.svg)
[![Discord](https://img.shields.io/discord/723455000604573736.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.com/channels/723455000604573736)

Build Kubernetes Operators in Java without hassle. Inspired
by [operator-sdk](https://github.com/operator-framework/operator-sdk).

Our webpage with documentation is getting better every day: https://javaoperatorsdk.io/

Table of Contents
==========

1. [Features](#Features)
1. [Why build your own Operator?](#Why-build-your-own-Operator)
1. [Roadmap and Release Notes](#Roadmap-and-Release-Notes)
1. [Join us on Discord!](#Join-us-on-Discord)
1. [Usage](#Usage)

## Features

* Framework for handling Kubernetes API events
* Automatic registration of Custom Resource watches
* Retry action on failure
* Smart event scheduling (only handle the latest event for the same resource)
* Handling Observed Generations automatically
* for all see [features](https://javaoperatorsdk.io/docs/features) section on the webpage

Check out
this [blog post](https://csviri.medium.com/deep-dive-building-a-kubernetes-operator-sdk-for-java-developers-5008218822cb)
about the non-trivial yet common problems needed to be solved for every operator. In case you are interested how to
handle more complex scenarios take a look
on [event sources](https://csviri.medium.com/java-operator-sdk-introduction-to-event-sources-a1aab5af4b7b)
.

## Why build your own Operator?

* Infrastructure automation using the power and flexibility of Java.
  See [blog post](https://blog.container-solutions.com/cloud-native-java-infrastructure-automation-with-kubernetes-operators)
  .
* Provisioning of complex applications - avoiding Helm chart hell
* Integration with Cloud services - e.g. Secret stores
* Safer deployment of applications - only expose cluster to users by Custom Resources

#### Overview of the 1.9.0 changes

- The Spring Boot starters have been moved to their own repositories and are now found at:
    - https://github.com/java-operator-sdk/operator-framework-spring-boot-starter
    - https://github.com/java-operator-sdk/operator-framework-spring-boot-starter-test
- Updated Fabric8 client to version 5.4.0
- It is now possible to configure the controllers to not automatically add finalizers to resources. See the `Controller`
  annotation documentation for more details.
- Added the possibility to configure how many seconds the SDK will wait before terminating reconciliation threads when a
  shut down is requested

#### Overview of the 1.8.0 changes

- The quarkus extension has been moved to the quarkiverse and is now found at
  https://github.com/quarkiverse/quarkus-operator-sdk

##### Overview of the 1.7.0 changes

- `Doneable` classes have been removed along with all the involved complexity
- `Controller` annotation has been simplified: the `crdName` field has been removed as that value is computed from the
  associated custom resource implementation
- Custom Resource implementation classes now need to be annotated with `Group` and `Version`
  annotations so that they can be identified properly. Optionally, they can also be annotated with
  `Kind` (if the name of the implementation class doesn't match the desired kind) and `Plural` if the plural version
  cannot be automatically computed (or the default computed version doesn't match your expectations).
- The `CustomResource` class that needs to be extended is now parameterized with spec and status types, so you can have
  an empty default implementation that does what you'd expect. If you don't need a status, using `Void` for the
  associated type should work.
- Custom Resources that are namespace-scoped need to implement the `Namespaced` interface so that the client can
  generate the proper URLs. This means, in particular, that `CustomResource`
  implementations that do **not** implement `Namespaced` are considered cluster-scoped. As a consequence,
  the `isClusterScoped` method/field has been removed from the appropriate classes (`Controller` annotation, in
  particular) as this is now inferred from the `CustomResource`
  type associated with your `Controller`.

Many of these changes might not be immediately apparent but will result in `404` errors when connecting to the cluster.
Please check that the Custom Resource implementations are properly annotated and that the value corresponds to your CRD
manifest. If the namespace appear to be missing in your request URL, don't forget that namespace-scoped Custom Resources
need to implement the `Namescaped` interface.

## Join us on Discord!

[Discord Invite Link](https://discord.gg/DacEhAy)

## Usage

We have several simple Operators under the [smoke-test-samples](smoke-test-samples) directory:

* *pure-java*: Minimal Operator implementation which only parses the Custom Resource and prints to stdout. Implemented
  with and without Spring Boot support. The two samples share the common module.
* *spring-boot-plain*: Sample showing integration with Spring Boot.

There are also more complete samples in the standalone [sample-operators](sample-operators):

* *webserver*: Simple example creating an NGINX webserver from a Custom Resource containing HTML code.
* *mysql-schema*: Operator managing schemas in a MySQL database.
* *tomcat*: Operator with two controllers, managing Tomcat instances and Webapps for these.

Add [dependency](https://search.maven.org/search?q=a:operator-framework) to your project with Maven:

```xml

<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>{see https://search.maven.org/search?q=a:operator-framework for latest version}</version>
</dependency>
```

Or alternatively with Gradle, which also requires declaring the SDK as an annotation processor to generate the mappings
between controllers and custom resource classes:

```groovy
dependencies {
    implementation "io.javaoperatorsdk:operator-framework:${javaOperatorVersion}"
    annotationProcessor "io.javaoperatorsdk:operator-framework:${javaOperatorVersion}"
}
```

Once you've added the dependency, define a main method initializing the Operator and registering a controller.

```java
public class Runner {

    public static void main(String[] args) {
        Operator operator = new Operator(DefaultConfigurationService.instance());
        operator.register(new WebPageReconciler());
        operator.start();
    }
}
```

The Controller implements the business logic and describes all the classes needed to handle the CRD.

```java

@ControllerConfiguration
public class WebServerController implements Reconciler<WebServer> {

    // Return the changed resource, so it gets updated. See javadoc for details.
    @Override
    public UpdateControl<CustomService> reconcile(CustomService resource,
                                                  Context context) {
        // ... your logic ...
        return UpdateControl.updateStatus(resource);
    }
}
```

A sample custom resource POJO representation

```java

@Group("sample.javaoperatorsdk")
@Version("v1")
public class WebPage extends CustomResource<WebPageSpec, WebPageStatus> implements
        Namespaced {

}

public class WebPageSpec {

    private String html;

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }
}
```

### Deactivating CustomResource implementations validation

The operator will, by default, query the deployed CRDs to check that the `CustomResource`
implementations match what is known to the cluster. This requires an additional query to the cluster and, sometimes,
elevated privileges for the operator to be able to read the CRDs from the cluster. This validation is mostly meant to
help users new to operator development get started and avoid common mistakes. Advanced users or production deployments
might want to skip this step. This is done by setting the `CHECK_CRD_ENV_KEY` environment variable to `false`.

### Automatic generation of CRDs

To automatically generate CRD manifests from your annotated Custom Resource classes, you only need to add the following
dependencies to your project:

```xml

<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-apt</artifactId>
    <scope>provided</scope>
</dependency>
```

The CRD will be generated in `target/classes/META-INF/fabric8` (or in `target/test-classes/META-INF/fabric8`, if you use
the `test` scope) with the CRD name suffixed by the generated spec version. For example, a CR using
the `java-operator-sdk.io` group with a `mycrs` plural form will result in 2 files:

- `mycrs.java-operator-sdk.io-v1.yml`
- `mycrs.java-operator-sdk.io-v1beta1.yml`

**NOTE:**
> Quarkus users using the `quarkus-operator-sdk` extension do not need to add any extra dependency to get their CRD generated as this is handled by the extension itself.

### Quarkus

A [Quarkus](https://quarkus.io) extension is also provided to ease the development of Quarkus-based operators.

Add [this dependency](https://search.maven.org/search?q=a:quarkus-operator-sdk)
to your project:

```xml

<dependency>
    <groupId>io.quarkiverse.operatorsdk</groupId>
    <artifactId>quarkus-operator-sdk</artifactId>
    <version>{see https://search.maven.org/search?q=a:quarkus-operator-sdk for latest version}
    </version>
</dependency>
```

Create an Application, Quarkus will automatically create and inject a `KubernetesClient` (
or `OpenShiftClient`), `Operator`, `ConfigurationService` and `ResourceController` instances that your application can
use. Below, you can see the minimal code you need to write to get your operator and controllers up and running:

```java

@QuarkusMain
public class QuarkusOperator implements QuarkusApplication {

    @Inject
    Operator operator;

    public static void main(String... args) {
        Quarkus.run(QuarkusOperator.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        operator.start();
        Quarkus.waitForExit();
        return 0;
    }
}
```

### Spring Boot

You can also let Spring Boot wire your application together and automatically register the controllers.

Add [this dependency](https://search.maven.org/search?q=a:operator-framework-spring-boot-starter) to your project:

```xml

<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework-spring-boot-starter</artifactId>
    <version>{see https://search.maven.org/search?q=a:operator-framework-spring-boot-starter for
        latest version}
    </version>
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

#### Spring Boot test support

Adding the following dependency would let you mock the operator for the tests where loading the spring container is
necessary, but it doesn't need real access to a Kubernetes cluster.

```xml

<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework-spring-boot-starter-test</artifactId>
    <version>{see https://search.maven.org/search?q=a:operator-framework-spring-boot-starter for
        latest version}
    </version>
</dependency>
``` 

Mock the operator:

```java

@SpringBootTest
@EnableMockOperator
public class SpringBootStarterSampleApplicationTest {

    @Test
    void contextLoads() {
    }
}
```
