---
title: Using sample Operators
description: How to use sample Operators
layout: docs
permalink: /docs/using-samples
---

# Sample Operators we Provide

We have few simple Operators under
the [smoke-test-samples](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/smoke-test-samples) directory.
These are used mainly to showcase some minimal operators, but also to do some sanity checks during development:

* *pure-java*: Minimal Operator implementation which only parses the Custom Resource and prints to stdout. Implemented
  with and without Spring Boot support. The two samples share the common module.
* *spring-boot-plain*: Sample showing integration with Spring Boot.

In addition to that, there are examples under [sample-operators](https://github.com/java-operator-sdk/java-operator-sdk/tree/master/sample-operators)
directory which are intended to show usage of different components in different scenarios, but mainly are more real world
examples:

* *webpage*: Simple example creating an NGINX webserver from a Custom Resource containing HTML code.
* *mysql-schema*: Operator managing schemas in a MySQL database. Shows how to manage non Kubernetes resources.
* *tomcat*: Operator with two controllers, managing Tomcat instances and Webapps running in Tomcat. The intention 
  with this example to show how to manage multiple related custom resources and/or more controllers.

# Implementing a Sample Operator

Add [dependency](https://search.maven.org/search?q=a:operator-framework%20AND%20g:io.javaoperatorsdk) to your project with Maven:

```xml

<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>{see https://search.maven.org/search?q=a:operator-framework%20AND%20g:io.javaoperatorsdk for latest version}</version>
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
public class WebPageReconciler implements Reconciler<WebPage> {

    // Return the changed resource, so it gets updated. See javadoc for details.
    @Override
    public UpdateControl<CustomService> reconcile(CustomService resource,
                                                               Context context) {
        // ... your logic ...
        return UpdateControl.patchStatus(resource);
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

### Deactivating CustomResource implementations validation

The operator will, by default, query the deployed CRDs to check that the `CustomResource`
implementations match what is known to the cluster. This requires an additional query to the cluster and, sometimes,
elevated privileges for the operator to be able to read the CRDs from the cluster. This validation is mostly meant to
help users new to operator development get started and avoid common mistakes. Advanced users or production deployments
might want to skip this step. This is done by setting the `CHECK_CRD_ENV_KEY` environment variable to `false`.

### Automatic generation of CRDs

To automatically generate CRD manifests from your annotated Custom Resource classes, you only need to add the following
dependencies to your project (in the background an annotation processor is used), with Maven:

```xml

<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-apt</artifactId>
    <scope>provided</scope>
</dependency>
```

or with Gradle:

```groovy
dependencies {
    annotationProcessor 'io.fabric8:crd-generator-apt:<version>'
    ...
}
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

Add [this dependency](https://search.maven.org/search?q=a:operator-framework-spring-boot-starter%20AND%20g:io.javaoperatorsdk) to your project:

```xml

<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework-spring-boot-starter</artifactId>
    <version>{see https://search.maven.org/search?q=a:operator-framework-spring-boot-starter%20AND%20g:io.javaoperatorsdk for
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

You will also need a `@Configuration` to make sure that your reconciler is registered: 

```java

@Configuration
public class Config {

    @Bean
    public WebPageReconciler customServiceController() {
        return new WebPageReconciler();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @SuppressWarnings("rawtypes")
    public Operator operator(List<Reconciler> controllers) {
        Operator operator = new Operator();
        controllers.forEach(operator::register);
        return operator;
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
    <version>{see https://search.maven.org/search?q=a:operator-framework-spring-boot-starter%20AND%20g:io.javaoperatorsdk for
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
